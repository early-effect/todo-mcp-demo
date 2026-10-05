package todo.server

import heddle.{ServerError as _, *}
import heddle.http.{Method, Request, Response, Status}
import heddle.mcp.{Mcp, ServedResource}
import heddle.mcp.apps.{UiDocument, withApp}
import heddle.mcp.protocol.{Resource, ResourceContents}
import heddle.sse.{ServerSentEvent, Sse, SseField}
import todo.*
import zio.*
import zio.json.*
import zio.stream.ZStream

/** The one board. HTTP routes and the MCP server both call it, and a change is pushed on both channels. */
final class TodoService private (
    board: Ref[Board],
    ids: Ref[Long],
    hub: Hub[Board],
    ping: Ref[UIO[Unit]],
):
  val api: Api[Any] =
    def save(next: Board): UIO[Board] =
      board.set(next) *> hub.publish(next) *> ping.get.flatten.as(next)

    def change(f: Board => Either[TodoError, Board]): ZIO[Any, TodoError, Board] =
      board.get.flatMap { current =>
        f(current) match
          case Left(err)   => ZIO.fail(err)
          case Right(next) => save(next)
      }

    Api("Todos", "0")
      .bind(Endpoints.show)(_ => board.get)
      .bind(Endpoints.add) { in =>
        val text = in.text.trim
        if text.isEmpty then ZIO.fail(TodoError.EmptyText)
        else
          ids.updateAndGet(_ + 1).flatMap { n =>
            board.get.flatMap(current => save(current.add(Todo(TodoId.mint(n), text, completed = false))))
          }
      }
      .bind(Endpoints.toggle)(in => change(_.toggle(in.id)))
      .bind(Endpoints.toggleAll)(_ => board.get.flatMap(current => save(current.toggleAll)))
      .bind(Endpoints.delete)(in => change(_.delete(in.id)))
      .bind(Endpoints.clear)(_ => board.get.flatMap(current => save(current.clearCompleted)))
      .bind(Endpoints.rename)(in => change(_.rename(in.id, in.text)))
      .bind(Endpoints.move)(in => change(_.move(in.dragged, in.target)))

  /** Current board, then each later one. The hub is subscribed before the current read, so a save in between is kept.
    */
  def events: Response =
    val stream = ZStream.unwrapScoped {
      hub.subscribe.map { queue =>
        ZStream.fromZIO(board.get).map(encode) ++ ZStream.fromQueue(queue).map(encode)
      }
    }
    Sse.response(stream)

  def mcp(viewJs: String): Either[ServerError, Mcp[Any]] =
    val resource = Resource(TodoShed.boardUri, "board", mimeType = Some("application/json"))
    val served   = ServedResource(
      resource,
      board.get.map { current =>
        Chunk(ResourceContents.Text(TodoShed.boardUri, Some("application/json"), current.toJson, None))
      },
    )
    for
      base <- Mcp.from(api).left.map(ServerError.Mcp(_))
      app  <- base.withApp(TodoShed.shed, UiDocument("Todos", viewJs)).left.map(ServerError.App(_))
      both <- app.withResources(served).left.map(ServerError.Mcp(_))
    yield both

  /** Later saves publish `notifications/resources/updated` for the board. */
  def arm(mcp: Mcp[Any]): UIO[Unit] = ping.set(mcp.resourceUpdated(TodoShed.boardUri))

  /** Board HTTP, then the MCP transport for whatever the board did not answer. */
  def dispatch(mcp: Mcp[Any])(req: Request): ZIO[Any, Response, Response] =
    (req.method, req.path.segments.toList) match
      case (Method.GET, "todos" :: "events" :: Nil) => ZIO.succeed(events)
      case _                                        =>
        api.routes(req).flatMap { res =>
          if res.status == Status.NotFound then mcp.routes(req) else ZIO.succeed(res)
        }

  private def encode(board: Board): ServerSentEvent =
    ServerSentEvent(board.toJson, Some(SseField("board")))
end TodoService

object TodoService:
  def make: UIO[TodoService] =
    for
      board <- Ref.make(Board.empty)
      ids   <- Ref.make(0L)
      hub   <- Hub.sliding[Board](1)
      ping  <- Ref.make[UIO[Unit]](ZIO.unit)
    yield TodoService(board, ids, hub, ping)
end TodoService
