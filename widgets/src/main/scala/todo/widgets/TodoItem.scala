package todo.widgets

import ascent.*
import ascent.css.Styles.*
import ascent.dsl.*
import todo.*
import zio.*

/** A single todo row, bound reactively to a per-item `Squawk[Todo]`. [[TodoList]]'s `forEach` builds the `<li>` once
  * per key; field changes flow through this row's own boundaries (class, `checked`, label text, the editing `when`).
  */
object TodoItem:

  /** A short pulse when an interaction lands (e.g. checking a todo). */
  object Pulse
      extends Keyframes(
        Frame.from(transform(Transform.scale(1))),
        Frame.pct(45)(transform(Transform.scale(1.18))),
        Frame.to(transform(Transform.scale(1))),
      )

  /** A short accent-glow flash on the row when a todo is completed. */
  object RowFlash
      extends Keyframes(
        Frame.from(background(Theme.accent.alpha(0.18))),
        Frame.to(background(Theme.accent.alpha(0.0))),
      )

  /** The `<li>` row: the checkbox, the label, and the destroy button. */
  object Row
      extends CssClass(
        position.relative,
        fontSize.px(18),
        borderBottom(Border.solid(1.px, Theme.divider)),
        display.flex,
        alignItems.center,
        // Right padding keeps the destroy button clear of the rounded card edge.
        padding(0, 20.px, 0, 0),
        minHeight.px(60),
        transition(Transition(background, Time.s(0.2))),
        Theme.FadeSlideIn.use(Time.s(0.35), TimingFunction.easeOut, fill = Some(SingleAnimationFillMode.Both)),
        Selector(PseudoClass.hover, background(Theme.accent.alpha(0.04))),
        Selector(PseudoClass.lastChild, borderBottom.none),
        Selector(
          Sel.descendant(Cls("toggle")),
          flex(0, 0, Length.auto),
          width.px(22),
          height.px(22),
          margin(0, 14.px, 0, 20.px),
          cursor.pointer,
          accentColor(Theme.accent),
          transition(Transition(transform, Time.s(0.2))),
          Selector(PseudoClass.hover, transform(Transform.scale(1.15))),
          Selector(PseudoClass.checked, Pulse.use(Time.s(0.4))),
        ),
        Selector(
          Sel.descendant(Elem.label),
          flex(1, 1, Length.auto),
          padding(18.px, 12.px),
          wordBreak.breakWord,
          cursor.pointer,
          color(Theme.onInk),
          transition(Transition(color, Time.s(0.3))),
        ),
        Selector(
          Sel.descendant(Cls("destroy")),
          flex(0, 0, Length.auto),
          width.px(36),
          height.px(36),
          margin(0, 0, 0, 8.px),
          fontSize.px(22),
          lineHeight(1),
          color(Theme.onInkFaint),
          background(Color.transparent),
          border.none,
          borderRadius.px(50),
          cursor.pointer,
          opacity(0.0),
          transition(
            Transition.list(
              Transition(opacity, Time.s(0.2)),
              Transition(color, Time.s(0.2)),
              Transition(transform, Time.s(0.2)),
              Transition(background, Time.s(0.2)),
            )
          ),
          Selector(
            PseudoClass.hover,
            color(Theme.danger),
            background(Theme.danger.alpha(0.12)),
            transform(Transform.list(Transform.rotate(Angle.deg(90)), Transform.scale(1.1))),
          ),
        ),
        Selector(PseudoClass.hover.descendant(Cls("destroy")), opacity(1.0)),
        Selector(PseudoClass.focusWithin.descendant(Cls("destroy")), opacity(1.0)),
        // Keep the destroy button visible while it itself holds keyboard focus.
        Selector(Sel.descendant(Cls("destroy").pseudoClass(PseudoClass.focusVisible)), opacity(1.0)),
        Selector(Sel.attr("draggable", AttrOp.Eq, "true"), cursor.grab),
      )

  /** Applied alongside [[Row]] when a todo is completed. */
  object Completed
      extends CssClass(
        RowFlash.use(Time.s(0.5), TimingFunction.easeOut),
        Selector(
          Sel.descendant(Elem.label),
          color(Theme.onInkDim),
          textDecoration.lineThrough,
          textDecorationColor(Theme.accentSoft),
          textDecorationThickness.px(1.5),
          transition(Transition(color, Time.s(0.3))),
        ),
      )

  /** The inline edit input shown while a row is being renamed. */
  object EditInput
      extends CssClass(
        flex(1, 1, Length.auto),
        padding(14.px, 18.px),
        margin(8.px, 16.px),
        fontSize.px(18),
        color(Theme.onInk),
        background(Color.rgba(0, 0, 0, 0.35)),
        fontFamily.inherit,
        border(Border.solid(1.px, Theme.accentSoft)),
        borderRadius.px(8),
        boxShadow(Shadow(Length.zero, Length.zero, Length.zero, Length.px(3), Theme.accentGlow)),
        outline.none,
        caretColor(Theme.accent),
      )

  /** The row being dragged: dimmed, with the move cursor. */
  object Dragging
      extends CssClass(
        opacity(0.4),
        cursor.grabbing,
      )

  /** The drop target: a top-border marks where the dragged row will land (inserted before it). */
  object DropTarget
      extends CssClass(
        boxShadow(Shadow.inset(Length.zero, Length.px(3), Length.zero, Length.zero, Theme.accent)),
        background(Theme.accent.alpha(0.06)),
      )

  def render(
      act: Act
  )(
      id: TodoId,
      item: ascent.squawk.Squawk[Todo],
      editing: ascent.squawk.Squawk[Boolean],
      draggable: ascent.squawk.Squawk[Boolean],
  ): UI[Any] =
    val classes = item.map(t => if t.completed then Set(Row, Completed) else Set[CssClass](Row))
    val text    = item.map(_.text)
    val label   = item.map { t =>
      val verb = if t.completed then "Mark as not completed" else "Mark as completed"
      s"""$verb: "${t.text}""""
    }
    E.li(
      classes,
      A.draggable(draggable),
      Ev.sync.onDragStart { e =>
        e.dataTransfer.foreach { data =>
          data.setData("text/plain", TodoId.raw(id))
          data.effectAllowed = "move"
        }
        e.currentTarget.foreach(_.addCssClass(Dragging))
      },
      Ev.sync.onDragEnd(e => e.currentTarget.foreach(_.removeCssClass(Dragging))),
      Ev.sync.onDragOver { e =>
        e.preventDefault()
        e.dataTransfer.foreach(_.dropEffect = "move")
        e.currentTarget.foreach(_.addCssClass(DropTarget))
      },
      Ev.sync.onDragLeave(e => e.currentTarget.foreach(_.removeCssClass(DropTarget))),
      Ev.onDrop { e =>
        ZIO.succeed {
          e.preventDefault()
          e.currentTarget.foreach(_.removeCssClass(DropTarget))
        } *> {
          val dragged = e.dataTransfer.map(_.getData("text/plain")).filter(_.nonEmpty).flatMap(TodoId.from(_).toOption)
          ZIO.foreachDiscard(dragged)(act.move(_, id))
        }
      },
      when(editing.map(!_)) {
        fragment(
          E.input(
            A.className("toggle"),
            A.typ("checkbox"),
            Aria.ariaLabel(label),
            A.checked(item.map(_.completed)),
            Ev.onChange(_ => act.toggle(id)),
          ),
          E.label(Ev.onDblClick(_ => act.edit(id)), text),
          E.button(
            A.className("destroy"),
            A.typ("button"),
            Tooltip("Delete this todo", Tooltip.Position.Left),
            Ev.onClick(_ => act.delete(id)),
            "×",
          ),
        )
      },
      when(editing) { editingView(act, id, item) },
    )

  private def editingView(act: Act, id: TodoId, item: ascent.squawk.Squawk[Todo]): UI[Any] =
    val draft = new StringBuilder
    E.input(
      EditInput,
      A.typ("text"),
      Aria.ariaLabel("Edit todo (Enter to save, Esc to cancel)"),
      A.autofocus(true),
      A.value(item.map(_.text)),
      Events.onInput(e =>
        ZIO.succeed {
          draft.clear()
          draft.append(e.targetValue.getOrElse(""))
        }
      ),
      Events.onBlur(_ => act.commit(id, draft.toString)),
      Events.onKeyDown(e =>
        e.key match
          case Some("Enter")  => act.commit(id, draft.toString)
          case Some("Escape") => act.cancel(id)
          case _              => ZIO.unit
      ),
    )
end TodoItem
