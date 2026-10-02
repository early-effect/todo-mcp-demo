package todo

import heddle.*
import heddle.http.Status

object Endpoints:
  private val rejected =
    (ep: Endpoint[?, ?, Board]) =>
      ep.outErrors[TodoError](
        ErrorCase[TodoError.EmptyText.type](Status.UnprocessableContent),
        ErrorCase[TodoError.UnknownTodo](Status.UnprocessableContent),
      )

  val show: Endpoint[Unit, Nothing, Board] =
    Endpoint.get("todos").out[Board].summary("Show the board").mcp("show_todos")

  val add: Endpoint[AddTodo, TodoError, Board] =
    rejected(
      Endpoint.post("todos").inJson[AddTodo].out[Board].summary("Add a todo").mcp("add_todo")
    )

  val toggle: Endpoint[TodoRef, TodoError, Board] =
    rejected(
      Endpoint.post("todos" / "toggle").inJson[TodoRef].out[Board].summary("Toggle a todo").mcp("toggle_todo")
    )

  val toggleAll: Endpoint[Unit, Nothing, Board] =
    Endpoint.post("todos" / "toggle-all").out[Board].summary("Toggle every todo").mcp("toggle_all")

  val delete: Endpoint[TodoRef, TodoError, Board] =
    rejected(
      Endpoint.post("todos" / "delete").inJson[TodoRef].out[Board].summary("Delete a todo").mcp("delete_todo")
    )

  val clear: Endpoint[Unit, Nothing, Board] =
    Endpoint.post("todos" / "clear").out[Board].summary("Clear completed todos").mcp("clear_completed")

  val rename: Endpoint[RenameTodo, TodoError, Board] =
    rejected(
      Endpoint.post("todos" / "rename").inJson[RenameTodo].out[Board].summary("Rename a todo").mcp("rename_todo")
    )

  val move: Endpoint[MoveTodo, TodoError, Board] =
    rejected(
      Endpoint.post("todos" / "move").inJson[MoveTodo].out[Board].summary("Move a todo").mcp("move_todo")
    )
end Endpoints
