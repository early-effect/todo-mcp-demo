package todo.widgets

import ascent.*
import ascent.css.Styles.*
import ascent.dsl.*
import todo.{Filter as TodoFilter, TodoId}

/** The main todo list area: a toggle-all button and the filtered `<ul>` of todo rows. */
object TodoList:

  /** The `<section>` wrapping the toggle-all button and the list. */
  object Section
      extends CssClass(
        position.relative
      )

  /** The toggle-all button (chevron + label) above the list. */
  object ToggleAll
      extends CssClass(
        display.flex,
        alignItems.center,
        gap.px(8),
        width.pct(100),
        padding(8.px, 18.px, 8.px, 12.px),
        border.none,
        borderBottom(Border.solid(1.px, Theme.divider)),
        background(Color.transparent),
        textAlign.left,
        fontSize.px(13),
        fontWeight(500),
        textTransform.uppercase,
        letterSpacing.px(2),
        color(Theme.onInkDim),
        cursor.pointer,
        transition(
          Transition.list(
            Transition(color, Time.s(0.2)),
            Transition(background, Time.s(0.2)),
          )
        ),
        Selector(PseudoClass.hover, color(Theme.cyan), background(Theme.cyan.alpha(0.04))),
        Selector(Sel.descendant(Elem.canvas), display.inlineBlock, verticalAlign.middle),
      )

  /** The `<ul>` holding the todo rows. */
  object Items
      extends CssClass(
        margin.zero,
        padding.zero,
        listStyle.none,
      )

  def component(shell: ascent.squawk.Squawk[Shell], filter: ascent.squawk.Squawk[TodoFilter], act: Act) =
    val visible     = ascent.squawk.Squawk.zipWith(shell, filter)((s, f) => s.board.visible(f).toSeq)
    val reorderable = filter.map(_ == TodoFilter.All)
    E.section(
      Section,
      Aria.role("main"),
      E.button(
        ToggleAll,
        A.typ("button"),
        Tooltip("Mark every todo as completed (or uncomplete all if all are done)", Tooltip.Position.Bottom),
        Ev.onClick(_ => act.toggleAll),
        ChevronCanvas(width = 56, height = 28),
        " Toggle all",
      ),
      E.ul(
        Items,
        Aria.role("list"),
        Aria.ariaLabel("Todo items"),
        forEach(visible)(t => TodoId.raw(t.id)) { todo =>
          val item    = shell.map(_.board.todos.find(_.id == todo.id).getOrElse(todo))
          val editing = shell.map(_.editing.contains(todo.id))
          TodoItem.render(act)(todo.id, item, editing, reorderable)
        },
      ),
    )
end TodoList
