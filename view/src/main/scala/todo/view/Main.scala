package todo.view

import ascent.*
import ascent.dsl.*
import ascent.js.{AscentApp, Subscriptions}
import ascent.squawk.{Source, sq}
import heddle.mcp.apps.ui.{AppBridge, HostContext, PostMessageBridge, Run}
import heddle.mcp.client.McpError
import heddle.mcp.protocol.{Implementation, ResourceContents}
import todo.*
import todo.widgets.*
import zio.*
import zio.json.*

/** The MCP App. It renders the same widgets as the page and talks only to its host. */
object Main extends ZIOAppDefault:
  def run =
    ZIO.scoped {
      for
        root <- ZIO
          .succeed(dom.document.getElementById("app"))
          .someOrElseZIO(ZIO.dieMessage("the view document has no #app"))
        subs   <- Ref.make(Option.empty[Subscriptions])
        state  <- sq(Shell.empty)
        filter <- Hash.filter
        bridge <- AppBridge.connect(
          TodoShed.shed,
          PostMessageBridge.toParent,
          AppBridge.Settings(
            Implementation("todo-view", "0"),
            onTeardown = subs.get.flatMap(ZIO.foreachDiscard(_)(_.cancelAll)),
          ),
        )
        run     <- sq[Run[Unit, Nothing, Board]](Run.waiting)
        _       <- bridge.run.foreach(run.set).forkScoped
        context <- sq(bridge.host.hostContext)
        _       <- bridge.context.foreach(context.set).forkScoped
        _       <- showReturned(run, state)
        _       <- followBoard(bridge, state)
        act = new Act:
          def setDraft(text: String): UIO[Unit] = state.update(_.copy(draft = text))
          def create: UIO[Unit]                 =
            state.get.flatMap { shell =>
              val text = shell.draft.trim
              if text.isEmpty then ZIO.unit
              else
                bridge
                  .call(_.add)(AddTodo(text))
                  .flatMap(board => state.update(_.copy(board = board, draft = "")))
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
              .flatMap(board => state.update(_.copy(board = board, editing = None)))
              .ignore
          def cancel(id: TodoId): UIO[Unit]                    = state.update(_.copy(editing = None))
          def move(dragged: TodoId, target: TodoId): UIO[Unit] =
            bridge.call(_.move)(MoveTodo(dragged, target)).flatMap(board => state.update(_.copy(board = board))).ignore
        mounted <- AscentApp.mount(themed(context, App.component(state, filter, act)), root)
        _       <- subs.set(Some(mounted))
        _       <- ZIO.addFinalizer(mounted.cancelAll)
        _       <- ZIO.never
      yield ()
    }

  private def showReturned(run: Source[Run[Unit, Nothing, Board]], state: Source[Shell]): URIO[Scope, Unit] =
    def show(board: Board): UIO[Unit] = remember(state, board)
    run.get.flatMap {
      case Run.Returned(_, board) => show(board)
      case _                      => ZIO.unit
    } *> run
      .observe {
        case Run.Returned(_, board) => show(board)
        case _                      => ZIO.unit
      }
      .forkScoped
      .unit

  private def remember(state: Source[Shell], board: Board): UIO[Unit] =
    state.update { shell =>
      shell.copy(board = board, editing = shell.editing.filter(id => board.todos.exists(_.id == id)))
    }

  private def themed(context: Source[HostContext], view: UI[Any]): UI[Any] =
    E.div(E.style(context.map(themeRule)), view)

  private def themeRule(context: HostContext): String =
    val decls = context.styles.toList.flatMap(_.variables.toList).sortBy(_._1.ordinal).map { (variable, value) =>
      s"${variable.wire}: ${value.filterNot(c => c == '{' || c == '}' || c == ';')};"
    }
    if decls.isEmpty then "" else decls.mkString(":root { ", " ", " }")

  /** The board resource, from the first read and each later update, until this scope closes. */
  private def followBoard[N <: Tuple, V <: Tuple](
      bridge: AppBridge[Unit, Nothing, Board, N, V],
      state: Source[Shell],
  ): URIO[Scope, Unit] =
    def latest: IO[McpError, Unit] =
      bridge.readResource(TodoShed.boardUri).flatMap { contents =>
        contents.collectFirst { case ResourceContents.Text(_, _, raw, _) => raw } match
          case None      => ZIO.unit
          case Some(raw) =>
            raw.fromJson[Board] match
              case Right(board) => remember(state, board)
              case Left(reason) => ZIO.logWarning(s"board decode: $reason")
      }
    if !bridge.offersResourceSubscribe then ZIO.logWarning("host did not offer board subscribe")
    else
      bridge.resourceUpdates.filter(_ == TodoShed.boardUri).foreach(_ => latest.ignore).forkScoped *>
        bridge.subscribeResource(TodoShed.boardUri).ignore *>
        latest.ignore

end Main
