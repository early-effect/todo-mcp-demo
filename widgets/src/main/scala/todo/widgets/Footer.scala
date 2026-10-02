package todo.widgets

import ascent.*
import ascent.css.Styles.*
import ascent.dsl.*
import todo.Filter as TodoFilter

/** Footer: live count of active todos, three filter buttons, and clear-completed. */
object Footer:

  /** The footer bar. Referenced by [[App.Glass]]'s container query (the bar stacks vertically in a narrow card). */
  object Bar
      extends CssClass(
        color(Theme.onInkDim),
        padding(14.px, 20.px),
        fontSize.px(13),
        letterSpacing.px(0.3),
        borderTop(Border.solid(1.px, Theme.divider)),
        display.flex,
        alignItems.center,
        justifyContent.spaceBetween,
        background(Color.rgba(10, 4, 24, 0.4)),
      )

  /** The `<ul>` of filter links (All / Active / Completed). */
  object Filters
      extends CssClass(
        display.flex,
        margin.zero,
        padding.zero,
        listStyle.none,
        Selector(Sel.descendant(Elem.li), margin(0, 4.px)),
        Selector(
          Sel.descendant(Elem.a),
          background(Color.transparent),
          border(Border.solid(1.px, Color.transparent)),
          color(Theme.onInkDim),
          padding(5.px, 12.px),
          cursor.pointer,
          borderRadius.px(20),
          fontSize.px(12),
          textTransform.uppercase,
          letterSpacing.px(1),
          textDecoration.none,
          transition(
            Transition.list(
              Transition(color, Time.s(0.2)),
              Transition(borderColor, Time.s(0.2)),
              Transition(background, Time.s(0.2)),
              Transition(boxShadow, Time.s(0.2)),
            )
          ),
          Selector(PseudoClass.hover, color(Theme.onInk), border(Border.solid(1.px, Theme.glassBorder))),
        ),
      )

  /** Applied alongside a [[Filters]] link when its filter is active. */
  object FilterSelected
      extends CssClass(
        color(Theme.accent),
        border(Border.solid(1.px, Theme.accentSoft)),
        background(Theme.accent.alpha(0.08)),
        boxShadow(Shadow(Length.zero, Length.zero, Length.px(12), Theme.accentGlow)),
      )

  /** The clear-completed button. */
  object Clear
      extends CssClass(
        background(Color.transparent),
        border.none,
        color(Theme.onInkDim),
        cursor.pointer,
        fontSize.px(12),
        textTransform.uppercase,
        letterSpacing.px(1),
        padding(4.px, 8.px),
        transition(Transition(color, Time.s(0.2))),
        Selector(PseudoClass.hover, color(Theme.danger), textDecoration.underline),
      )

  def component(shell: ascent.squawk.Squawk[Shell], filter: ascent.squawk.Squawk[TodoFilter], act: Act) =
    val todos         = shell.map(_.board.todos)
    val activeCount   = todos.map(_.count(!_.completed))
    val remainingText = activeCount.map(n => s"$n item${if n == 1 then "" else "s"} left")
    val anyCompleted  = todos.map(_.exists(_.completed))
    E.footer(
      Bar,
      Aria.role("contentinfo"),
      E.span(Aria.role("status"), Aria.ariaLive("polite"), Aria.ariaAtomic(true), remainingText),
      E.ul(
        Filters,
        Aria.role("group"),
        Aria.ariaLabel("Filter todos"),
        link(filter, TodoFilter.All, "All", "Show all todos"),
        link(filter, TodoFilter.Active, "Active", "Show only active todos"),
        link(filter, TodoFilter.Completed, "Completed", "Show only completed todos"),
      ),
      when(anyCompleted) {
        E.button(
          Clear,
          A.typ("button"),
          Tooltip("Permanently delete every completed todo", Tooltip.Position.Top),
          Ev.onClick(_ => act.clear),
          "Clear completed",
        )
      },
    )

  private def link(current: ascent.squawk.Squawk[TodoFilter], filter: TodoFilter, label: String, tip: String) =
    val classes = current.map(f => if f == filter then Set[CssClass](FilterSelected) else Set.empty[CssClass])
    val marked  = current.map(f => if f == filter then "page" else "false")
    E.li(
      E.a(
        classes,
        A.href(TodoFilter.toHash(filter)),
        Aria.ariaCurrent(marked),
        Tooltip(tip, Tooltip.Position.Top),
        label,
      )
    )
end Footer
