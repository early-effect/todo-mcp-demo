package todo.view

import ascent.*
import ascent.dsl.*
import ascent.history.History
import ascent.mcpapp.{AppInfo, McpApp}
import ascent.squawk.Source
import heddle.mcp.apps.ui.{PostMessageBridge, Run}
import todo.*
import todo.widgets.*
import zio.*

/** The MCP App. It renders the same widgets as the page and talks only to its host. */
object Main extends ZIOAppDefault:
  def run =
    for
      root <- ZIO
        .succeed(dom.document.getElementById("app"))
        .someOrElseZIO(ZIO.dieMessage("the view document has no #app"))
      state <- sq(Shell.empty)
      hist  <- History.browser
      filter = hist.location.map(loc => todo.Filter.fromHash(loc.hash))
      _ <- ui(state, filter).mount(PostMessageBridge.toParent, root, AppInfo("todo-view", "0"))
      _ <- ZIO.never
    yield ()

  private def ui(state: Source[Shell], filter: ascent.squawk.Squawk[todo.Filter]) =
    McpApp(TodoShed.shed).view { (run, bridge) =>
      val act = new Act:
        def setDraft(text: String): UIO[Unit] = state.update(_.copy(draft = text))
        def create: UIO[Unit]                 =
          state.get.flatMap { shell =>
            val text = shell.draft.trim
            if text.isEmpty then ZIO.unit
            else
              bridge
                .call(_.add)(AddTodo(text))
                .flatMap { board =>
                  state.update(_.copy(board = board, draft = ""))
                }
                .ignore
          }
        def toggle(id: TodoId): UIO[Unit] =
          bridge.call(_.toggle)(TodoRef(id)).flatMap(board => state.update(_.copy(board = board))).ignore
        def toggleAll: UIO[Unit] =
          bridge.call(_.toggleAll)(()).flatMap(board => state.update(_.copy(board = board))).ignore
        def delete(id: TodoId): UIO[Unit] =
          bridge.call(_.delete)(TodoRef(id)).flatMap(board => state.update(_.copy(board = board))).ignore
        def clear: UIO[Unit] =
          bridge.call(_.clear)(()).flatMap(board => state.update(_.copy(board = board))).ignore
        def edit(id: TodoId): UIO[Unit]                 = state.update(_.copy(editing = Some(id)))
        def commit(id: TodoId, text: String): UIO[Unit] =
          bridge
            .call(_.rename)(RenameTodo(id, text))
            .flatMap { board =>
              state.update(_.copy(board = board, editing = None))
            }
            .ignore
        def cancel(id: TodoId): UIO[Unit]                    = state.update(_.copy(editing = None))
        def move(dragged: TodoId, target: TodoId): UIO[Unit] =
          bridge.call(_.move)(MoveTodo(dragged, target)).flatMap(board => state.update(_.copy(board = board))).ignore

      E.div(
        // onMount runs inline on the mount fiber, and subscribe does not finish.
        Lifecycle.onMount[dom.HTMLDivElement] { _ =>
          def show(board: Board): UIO[Unit] =
            state.update { shell =>
              shell.copy(board = board, editing = shell.editing.filter(id => board.todos.exists(_.id == id)))
            }
          run.get.flatMap {
            case Run.Returned(_, board) => show(board)
            case _                      => ZIO.unit
          } *>
            run.observe {
              case Run.Returned(_, board) => show(board)
              case _                      => ZIO.unit
            } *>
            bridge
              .subscribe[Board](TodoShed.boardUri)(show)
              .catchAll(error => ZIO.logWarning(s"board subscribe: $error"))
              .fork
              .unit
        },
        App.component(state, filter, act),
      )
    }
end Main
