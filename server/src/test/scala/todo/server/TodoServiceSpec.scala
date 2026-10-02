package todo.server

import heddle.*
import heddle.client.CallFailure
import heddle.http.{Body, Request, Status}
import heddle.mcp.apps.UiMeta
import heddle.mcp.client.{McpCallFailure, McpClient}
import heddle.mcp.protocol.{ClientRequest, Message, Notifications, ResourceContents}
import heddle.mcp.transport.Http
import heddle.sse.{ServerSentEvent, SseCodec}
import todo.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

object TodoServiceSpec extends ZIOSpecDefault:
  private val info = heddle.mcp.protocol.Implementation("todo-service-spec", "0")

  def spec = suite("TodoService")(
    test("the board routes add, toggle, rename, move, and reject a blank") {
      for
        svc   <- TodoService.make
        mcp   <- ZIO.fromEither(svc.mcp("view"))
        _     <- svc.arm(mcp)
        story <- board.provide(Client.inMemory(Routes.fromHandler(Handler(svc.dispatch(mcp)))))
      yield story
    },
    test("a session hears todo://board when the board changes") {
      ZIO.scoped {
        for
          svc     <- TodoService.make
          mcp     <- ZIO.fromEither(svc.mcp("view"))
          _       <- svc.arm(mcp)
          session <- McpClient
            .http("http://todos.test/mcp", McpClient.Settings(info, handshake = McpClient.Handshake.Session))
            .provideSome[Scope](Client.inMemory(Routes.fromHandler(Handler(svc.dispatch(mcp)))))
          heard <- Queue.unbounded[Message.Notification]
          _     <- session.notifications.foreach(heard.offer).fork
          live  <- untilHeard(mcp, heard)
          _     <- session.request(ClientRequest.SubscribeResource(TodoShed.boardUri))
          milk  <- session.call(Endpoints.add)(AddTodo("milk"))
          note  <- nextUpdate(heard)
          body  <- session.readResource(TodoShed.boardUri)
        yield
          val uri  = note.flatMap(_.params.get("uri")).collect { case Json.Str(value) => value }
          val text = body.collectFirst { case ResourceContents.Text(_, _, raw, _) => raw.fromJson[Board] }
          assertTrue(live, uri.contains(TodoShed.boardUri), text.contains(Right(milk)), milk.todos.length == 1)
      }
    } @@ TestAspect.withLiveClock
    ,
    test("tools/list is the eight board tools, and the view asks for no network") {
      ZIO.scoped {
        for
          svc     <- TodoService.make
          mcp     <- ZIO.fromEither(svc.mcp(Sentinel))
          _       <- svc.arm(mcp)
          session <- McpClient
            .http("http://todos.test/mcp", McpClient.Settings(info, handshake = McpClient.Handshake.Session))
            .provideSome[Scope](Client.inMemory(Routes.fromHandler(Handler(svc.dispatch(mcp)))))
          tools   <- session.listTools
          view    <- session.readResource(TodoShed.shed.uri.value)
          current <- session.readResource(TodoShed.boardUri)
          shown   <- session.call(Endpoints.show)(())
          milk    <- session.call(Endpoints.add)(AddTodo("milk"))
          again   <- session.call(Endpoints.show)(())
          missing <- session.call(Endpoints.toggle)(TodoRef(TodoId.mint(99))).flip
        yield
          val names  = tools.map(_.name.value)
          val html   = view.collectFirst { case text: ResourceContents.Text => text }
          val board  = current.collectFirst { case ResourceContents.Text(_, _, raw, _) => raw.fromJson[Board] }
          val policy = html.map(text => UiMeta.decodeResource(text.meta)._1)
          assertTrue(
            names.toSet == Tools,
            names.length == 8,
            tools.forall(tool => UiMeta.decodeTool(tool.meta)._1.visibility.model),
            !names.exists(_.contains("events")),
            html.exists(_.mimeType.contains(UiMeta.MimeType)),
            html.exists(_.text.contains(Sentinel)),
            policy.exists(_.network.connect.isEmpty),
            board.contains(Right(Board.empty)),
            shown.todos.isEmpty,
            again == milk,
            milk.todos.map(_.text) == Chunk("milk"),
            missing == McpCallFailure.Domain(TodoError.UnknownTodo(TodoId.mint(99))),
          )
      }
    },
    test("the page event stream sends the current board, then the added one") {
      for
        svc    <- TodoService.make
        mcp    <- ZIO.fromEither(svc.mcp("view"))
        _      <- svc.arm(mcp)
        routes = Routes.fromHandler(Handler(svc.dispatch(mcp)))
        story <- ZIO.scoped {
          for
            res    <- ZIO.serviceWithZIO[Client](_.streaming(Request.get("/todos/events")))
            heard  <- Queue.unbounded[ServerSentEvent]
            _      <- SseCodec.stream(res.body.toStream.mapError(_ => None)).foreach(heard.offer).fork
            first  <- awaitEvent(heard)
            _      <- Client.call(Endpoints.add)(AddTodo("milk"))
            second <- awaitEvent(heard)
            denied <- routes(
              Request
                .post(
                  "/mcp",
                  Body.json(
                    Json
                      .Obj(
                        "jsonrpc" -> Json.Str("2.0"),
                        "id"      -> Json.Num(9),
                        "method"  -> Json.Str("resources/subscribe"),
                        "params"  -> Json.Obj(
                        "uri" -> Json.Str(TodoShed.boardUri),
                        "_meta" -> Json.Obj(
                          "io.modelcontextprotocol/protocolVersion" -> Json.Str(
                            heddle.mcp.protocol.ProtocolVersion.Current.value
                          )
                        ),
                      ),
                      )
                      .toJson
                  ),
                )
                .withHeader(Http.ProtocolHeader, heddle.mcp.protocol.ProtocolVersion.Current.value)
                .withHeader(Http.MethodHeader, "resources/subscribe")
            )
          yield
            val boards = List(first, second).flatten.flatMap(_.data.fromJson[Board].toOption)
            assertTrue(
              boards.headOption.exists(_.todos.isEmpty),
              boards.lift(1).map(_.todos.map(_.text)).contains(Chunk("milk")),
              denied.status == Status.BadRequest,
              denied.body.text.exists(_.contains("needs a session")),
            )
        }.provide(Client.inMemory(routes))
      yield story
    } @@ TestAspect.withLiveClock @@ TestAspect.timeout(10.seconds),
  )

  private val Sentinel: String = "window.todoViewSentinel = 1"

  private val Tools: Set[String] = Set(
    "show_todos",
    "add_todo",
    "toggle_todo",
    "toggle_all",
    "delete_todo",
    "clear_completed",
    "rename_todo",
    "move_todo",
  )

  /** The session GET is a forked fiber. A yield does not run it; a short sleep does. One notice means the ear is up. */
  private def untilHeard(mcp: heddle.mcp.Mcp[Any], heard: Queue[Message.Notification]): UIO[Boolean] =
    def go(left: Int): UIO[Boolean] =
      if left == 0 then ZIO.succeed(false)
      else
        mcp.toolsChanged *> ZIO.sleep(5.millis) *> heard.poll.flatMap {
          case Some(_) => ZIO.succeed(true)
          case None    => go(left - 1)
        }
    go(40)

  private def nextUpdate(heard: Queue[Message.Notification]): UIO[Option[Message.Notification]] =
    def go(left: Int): UIO[Option[Message.Notification]] =
      if left == 0 then ZIO.succeed(None)
      else
        heard.poll.flatMap {
          case Some(note) if note.method == Notifications.ResourceUpdated => ZIO.succeed(Some(note))
          case Some(_)                                                    => go(left - 1)
          case None                                                       => ZIO.sleep(5.millis) *> go(left - 1)
        }
    go(40)

  private def awaitEvent(heard: Queue[ServerSentEvent]): UIO[Option[ServerSentEvent]] =
    def go(left: Int): UIO[Option[ServerSentEvent]] =
      if left == 0 then ZIO.succeed(None)
      else
        heard.poll.flatMap {
          case Some(event) => ZIO.succeed(Some(event))
          case None        => ZIO.sleep(5.millis) *> go(left - 1)
        }
    go(40)

  private def board =
    for
      shown   <- Client.call(Endpoints.show)(())
      blank   <- Client.call(Endpoints.add)(AddTodo("  ")).flip
      milk    <- Client.call(Endpoints.add)(AddTodo("milk"))
      id      <- one(milk)
      done    <- Client.call(Endpoints.toggle)(TodoRef(id))
      back    <- Client.call(Endpoints.toggleAll)(())
      eggs    <- Client.call(Endpoints.add)(AddTodo("eggs"))
      egg     <- named(eggs, "eggs")
      moved   <- Client.call(Endpoints.move)(MoveTodo(egg, id))
      _       <- Client.call(Endpoints.toggle)(TodoRef(id))
      left    <- Client.call(Endpoints.clear)(())
      gone    <- Client.call(Endpoints.rename)(RenameTodo(egg, "  "))
      bread   <- Client.call(Endpoints.add)(AddTodo("bread"))
      bid     <- one(bread)
      empty   <- Client.call(Endpoints.delete)(TodoRef(bid))
      missing <- Client.call(Endpoints.toggle)(TodoRef(TodoId.mint(99))).flip
    yield assertTrue(
      shown.todos.isEmpty,
      blank == CallFailure.Domain(TodoError.EmptyText),
      done.todos.exists(t => t.id == id && t.completed),
      back.todos.exists(t => t.id == id && !t.completed),
      moved.todos.map(_.text) == Chunk("eggs", "milk"),
      left.todos.map(_.text) == Chunk("eggs"),
      gone.todos.isEmpty,
      empty.todos.isEmpty,
      rejected(missing),
    )

  private def rejected(failure: CallFailure[TodoError]): Boolean =
    failure match
      case CallFailure.Domain(TodoError.UnknownTodo(id)) => id == TodoId.mint(99)
      case _                                             => false

  private def one(board: Board): UIO[TodoId] =
    board.todos match
      case Chunk(todo) => ZIO.succeed(todo.id)
      case _           => ZIO.dieMessage(s"expected one todo, saw ${board.todos.length}")

  private def named(board: Board, text: String): UIO[TodoId] =
    board.todos.find(_.text == text) match
      case Some(todo) => ZIO.succeed(todo.id)
      case None       => ZIO.dieMessage(s"no todo named $text")
end TodoServiceSpec
