package todo.page

import ascent.*
import ascent.history.History
import ascent.js.AscentApp
import ascent.squawk.{Source, sq}
import heddle.client.Client
import heddle.http.Url
import todo.*
import todo.widgets.*
import zio.*
import zio.json.*
import heddle.mcp.apps.ui.ViewNotification.Log

object Main extends ZIOAppDefault:
  def run =
    ZIO
      .scoped {
        program
      }
      .provide(Client.live)

  private def program =
    for
      _      <- ZIO.log("Starting program")
      state  <- sq(Shell.empty)
      hist   <- History.browser
      origin <- ZIO
        .fromEither(Url.decode(dom.window.location.origin))
        .orDieWith(e => IllegalArgumentException(e.toString))
      client <- ZIO.service[Client]
      act    = Live(state, origin, client)
      filter = hist.location.map(loc => todo.Filter.fromHash(loc.hash))
      _ <- AscentApp.mountBody(App.component(state, filter, act))
      _ <- Client
        .sse(s"${dom.window.location.origin}/todos/events")
        .foreach { event =>
          event.data.fromJson[Board] match
            case Right(board) =>
              state.update(shell =>
                shell.copy(board = board, editing = shell.editing.filter(id => board.todos.exists(_.id == id)))
              )
            case Left(_) => ZIO.unit
        }
        .forkScoped
      _ <- act.refresh
      _ <- ZIO.never
    yield ()

  private final class Live(state: Source[Shell], origin: Url, client: Client) extends Act:
    private def call[In, Err](ep: heddle.Endpoint[In, Err, Board])(in: In): UIO[Unit] =
      Client
        .call(ep)
        .at(origin, in)
        .provideEnvironment(ZEnvironment(client))
        .flatMap(board => state.update(_.copy(board = board)))
        .ignore

    def refresh: UIO[Unit] = call(Endpoints.show)(())

    def setDraft(text: String): UIO[Unit] = state.update(_.copy(draft = text))

    def create: UIO[Unit] =
      state.get.flatMap { shell =>
        val text = shell.draft.trim
        if text.isEmpty then ZIO.unit
        else call(Endpoints.add)(AddTodo(text)) *> state.update(_.copy(draft = ""))
      }

    def toggle(id: TodoId): UIO[Unit]               = call(Endpoints.toggle)(TodoRef(id))
    def toggleAll: UIO[Unit]                        = call(Endpoints.toggleAll)(())
    def delete(id: TodoId): UIO[Unit]               = call(Endpoints.delete)(TodoRef(id))
    def clear: UIO[Unit]                            = call(Endpoints.clear)(())
    def edit(id: TodoId): UIO[Unit]                 = state.update(_.copy(editing = Some(id)))
    def commit(id: TodoId, text: String): UIO[Unit] =
      call(Endpoints.rename)(RenameTodo(id, text)) *> state.update(_.copy(editing = None))
    def cancel(id: TodoId): UIO[Unit]                    = state.update(_.copy(editing = None))
    def move(dragged: TodoId, target: TodoId): UIO[Unit] =
      call(Endpoints.move)(MoveTodo(dragged, target))
  end Live
end Main
