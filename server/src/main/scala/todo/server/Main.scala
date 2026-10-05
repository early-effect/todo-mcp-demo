package todo.server

import heddle.{ServerError as _, *}
import zio.*

object Main extends ZIOAppDefault:
  private val page     = load("page.html")
  private val hostPage = load("host.html")

  def run =
    for
      pageHtml <- page
      hostHtml <- hostPage
      pageJs   <- load("page.js")
      viewJs   <- load("todo-view.js")
      hostJs   <- load("todo-host.js")
      svc      <- TodoService.make
      mcp      <- ZIO.fromEither(svc.mcp(viewJs))
      _        <- svc.arm(mcp)
      routes = Assets.api(Html(pageHtml), Html(hostHtml), Javascript(pageJs), Javascript(hostJs)).routes ++
        Routes.fromHandler(Handler(svc.dispatch(mcp)))
      _ <- Server
        .serve(routes)
        .provide(Server.defaultWith(_.copy(host = "127.0.0.1", port = 8080)))
        .mapError(ServerError.Serve(_))
    yield ()

  private def load(name: String): IO[ServerError, String] =
    ZIO
      .attemptBlocking(Option(getClass.getResourceAsStream(s"/$name")))
      .mapError(ServerError.Unreadable(name, _))
      .flatMap {
        case None     => ZIO.fail(ServerError.Missing(name))
        case Some(in) =>
          ZIO
            .attemptBlocking(scala.util.Using.resource(scala.io.Source.fromInputStream(in))(_.mkString))
            .mapError(ServerError.Unreadable(name, _))
            .flatMap(text => if text.isEmpty then ZIO.fail(ServerError.Empty(name)) else ZIO.succeed(text))
      }
end Main
