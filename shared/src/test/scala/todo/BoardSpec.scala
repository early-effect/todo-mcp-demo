package todo

import zio.Chunk
import zio.test.*

object BoardSpec extends ZIOSpecDefault:
  private def id(raw: String): TodoId =
    TodoId.from(raw) match
      case Right(value) => value
      case Left(_)      => TodoId.mint(0)

  def spec = suite("Board")(
    test("add then toggle then delete returns empty") {
      val a         = id("1")
      val start     = Board.empty.add(Todo(a, "milk", completed = false))
      val toggled   = start.toggle(a)
      val deleted   = toggled.flatMap(_.delete(a))
      val completed = toggled.map(_.todos.exists(_.completed))
      assertTrue(deleted == Right(Board.empty), completed == Right(true))
    },
    test("blank text is not a board concern of add on the server, rename to blank deletes") {
      val a     = id("1")
      val start = Board.empty.add(Todo(a, "milk", completed = false))
      assertTrue(start.rename(a, "  ") == Right(Board.empty))
    },
    test("unknown id leaves the board unchanged") {
      val a       = id("1")
      val missing = id("nope")
      val start   = Board.empty.add(Todo(a, "milk", completed = false))
      assertTrue(start.toggle(missing) == Left(TodoError.UnknownTodo(missing)), start.todos.length == 1)
    },
    test("toggleAll flips every row, and clearCompleted drops the completed ones") {
      val a       = id("a")
      val b       = id("b")
      val start   = Board(Chunk(Todo(a, "a", false), Todo(b, "b", true)))
      val flipped = start.toggleAll
      val cleared = start.clearCompleted
      assertTrue(
        flipped.todos.forall(_.completed),
        flipped.toggleAll.todos.forall(t => !t.completed),
        cleared.todos.map(_.id) == Chunk(a),
      )
    },
    test("move puts dragged immediately before target") {
      val a     = id("a")
      val b     = id("b")
      val c     = id("c")
      val start = Board(Chunk(Todo(a, "a", false), Todo(b, "b", false), Todo(c, "c", false)))
      val moved = start.move(c, a)
      assertTrue(moved.map(_.todos.map(_.id)) == Right(Chunk(c, a, b)), start.move(a, a) == Right(start))
    },
  )
end BoardSpec
