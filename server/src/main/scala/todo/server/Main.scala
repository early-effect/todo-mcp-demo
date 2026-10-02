package todo.server

import heddle.*
import heddle.http.{Body, MediaType, Method, Request, Response, Status}
import zio.*

object Main extends ZIOAppDefault:
  private val page =
    """<!doctype html>
      |<html lang="en">
      |<head>
      |<meta charset="utf-8">
      |<meta name="viewport" content="width=device-width, initial-scale=1">
      |<title>Todos</title>
      |</head>
      |<body>
      |<script src="/page.js"></script>
      |</body>
      |</html>
      |""".stripMargin

  private val hostPage =
    """<!doctype html>
      |<html lang="en">
      |<head>
      |<meta charset="utf-8">
      |<meta name="viewport" content="width=device-width, initial-scale=1">
      |<title>Todos host</title>
      |</head>
      |<body>
      |<script src="/host.js"></script>
      |</body>
      |</html>
      |""".stripMargin

  def run =
    for
      pageJs <- load("page.js").orDie
      viewJs <- load("todo-view.js").orDie
      hostJs <- load("todo-host.js").orDie
      svc    <- TodoService.make
      mcp    <- ZIO.fromEither(svc.mcp(viewJs)).foldZIO(e => ZIO.dieMessage(explain(e)), ZIO.succeed)
      _      <- svc.arm(mcp)
      routes = Routes.fromHandler(Handler { (req: Request) =>
        (req.method, req.path.segments.toList) match
          case (Method.GET, Nil)              => ZIO.succeed(Response.html(page))
          case (Method.GET, "host" :: Nil)    => ZIO.succeed(Response.html(hostPage))
          case (Method.GET, "page.js" :: Nil) => ZIO.succeed(javascript(pageJs))
          case (Method.GET, "host.js" :: Nil) => ZIO.succeed(javascript(hostJs))
          case _                              => svc.dispatch(mcp)(req)
      })
      _ <- Server.serve(routes).provide(Server.defaultWith(_.copy(host = "127.0.0.1", port = 8080)))
    yield ()

  private def load(name: String): IO[IllegalStateException, String] =
    ZIO.attempt(Option(getClass.getResourceAsStream(s"/$name"))).orDie.flatMap {
      case None     => ZIO.fail(new IllegalStateException(s"$name is missing"))
      case Some(in) =>
        val text = scala.util.Using.resource(scala.io.Source.fromInputStream(in))(_.mkString)
        if text.isEmpty then ZIO.fail(new IllegalStateException(s"$name is empty")) else ZIO.succeed(text)
    }

  private def javascript(source: String): Response =
    Response(Status.Ok).withBody(Body.text(source, MediaType("text", "javascript")))

  private def explain(error: Startup): String = error match
    case Startup.Mcp(errors) => errors.map(_.message).mkString("; ")
    case Startup.App(errors) => errors.map(_.message).mkString("; ")
end Main
