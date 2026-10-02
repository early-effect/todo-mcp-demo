package todo

import heddle.endpoint.{Schema, SchemaDoc}
import zio.Chunk
import zio.json.*

opaque type TodoId = String

object TodoId:
  def from(raw: String): Either[TodoError, TodoId] =
    if raw.isEmpty then Left(TodoError.EmptyText) else Right(raw)

  def mint(n: Long): TodoId = n.toString

  def raw(id: TodoId): String = id

  given Schema[TodoId]    = Schema.of(SchemaDoc.Str(None))
  given JsonCodec[TodoId] = JsonCodec.string.transform(identity, identity)
end TodoId

final case class Todo(id: TodoId, text: String, completed: Boolean) derives Schema, JsonCodec

final case class Board(todos: Chunk[Todo]) derives Schema, JsonCodec:

  def add(todo: Todo): Board = copy(todos = todos :+ todo)

  def toggle(id: TodoId): Either[TodoError, Board] =
    update(id)(t => t.copy(completed = !t.completed))

  def delete(id: TodoId): Either[TodoError, Board] =
    if todos.exists(_.id == id) then Right(copy(todos = todos.filterNot(_.id == id)))
    else Left(TodoError.UnknownTodo(id))

  def rename(id: TodoId, text: String): Either[TodoError, Board] =
    if text.trim.isEmpty then delete(id) else update(id)(_.copy(text = text.trim))

  def toggleAll: Board =
    val done = todos.nonEmpty && todos.forall(_.completed)
    copy(todos = todos.map(_.copy(completed = !done)))

  def clearCompleted: Board = copy(todos = todos.filterNot(_.completed))

  def move(dragged: TodoId, target: TodoId): Either[TodoError, Board] =
    if dragged == target then Right(this)
    else
      val row       = todos.find(_.id == dragged)
      val hasTarget = todos.exists(_.id == target)
      (row, hasTarget) match
        case (None, _)       => Left(TodoError.UnknownTodo(dragged))
        case (_, false)      => Left(TodoError.UnknownTodo(target))
        case (Some(item), _) =>
          val rest            = todos.filterNot(_.id == dragged)
          val at              = rest.indexWhere(_.id == target)
          val (before, after) = rest.splitAt(at)
          Right(copy(todos = before ++ Chunk(item) ++ after))

  def visible(filter: Filter): Chunk[Todo] =
    filter match
      case Filter.All       => todos
      case Filter.Active    => todos.filterNot(_.completed)
      case Filter.Completed => todos.filter(_.completed)

  private def update(id: TodoId)(f: Todo => Todo): Either[TodoError, Board] =
    if todos.exists(_.id == id) then Right(copy(todos = todos.map(t => if t.id == id then f(t) else t)))
    else Left(TodoError.UnknownTodo(id))
end Board

object Board:
  val empty: Board = Board(Chunk.empty)

enum Filter:
  case All, Active, Completed

object Filter:
  def fromHash(hash: String): Filter =
    val bare = hash.stripPrefix("#").stripPrefix("/")
    bare match
      case "active"    => Filter.Active
      case "completed" => Filter.Completed
      case _           => Filter.All

  def toHash(filter: Filter): String =
    filter match
      case Filter.All       => "#/"
      case Filter.Active    => "#/active"
      case Filter.Completed => "#/completed"
end Filter

enum TodoError derives Schema, JsonCodec:
  case EmptyText
  case UnknownTodo(id: TodoId)

final case class AddTodo(text: String) derives Schema, JsonCodec
final case class TodoRef(id: TodoId) derives Schema, JsonCodec
final case class RenameTodo(id: TodoId, text: String) derives Schema, JsonCodec
final case class MoveTodo(dragged: TodoId, target: TodoId) derives Schema, JsonCodec
