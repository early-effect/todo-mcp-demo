package todo

import heddle.mcp.apps.{Grant, Shed, UiUri, Visibility}

object TodoShed:
  val boardUri: String = "todo://board"

  val shed =
    Shed(UiUri("ui://todo/app"), "Todos", Grant.launch(Endpoints.show))(
      (
        add = Grant(Endpoints.add, Visibility.ModelAndApp),
        toggle = Grant(Endpoints.toggle, Visibility.ModelAndApp),
        toggleAll = Grant(Endpoints.toggleAll, Visibility.ModelAndApp),
        delete = Grant(Endpoints.delete, Visibility.ModelAndApp),
        clear = Grant(Endpoints.clear, Visibility.ModelAndApp),
        rename = Grant(Endpoints.rename, Visibility.ModelAndApp),
        move = Grant(Endpoints.move, Visibility.ModelAndApp),
      )
    )
end TodoShed
