package todo.widgets

import todo.*
import zio.*

final case class Shell(board: Board, draft: String, editing: Option[TodoId])

object Shell:
  val empty: Shell = Shell(Board.empty, "", None)

/** What the widgets can ask the page or the view to do. Draft and editing stay here; the board does not. */
trait Act:
  def setDraft(text: String): UIO[Unit]
  def create: UIO[Unit]
  def toggle(id: TodoId): UIO[Unit]
  def toggleAll: UIO[Unit]
  def delete(id: TodoId): UIO[Unit]
  def clear: UIO[Unit]
  def edit(id: TodoId): UIO[Unit]
  def commit(id: TodoId, text: String): UIO[Unit]
  def cancel(id: TodoId): UIO[Unit]
  def move(dragged: TodoId, target: TodoId): UIO[Unit]
end Act
