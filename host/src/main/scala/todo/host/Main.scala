package todo.host

import ascent.dom
import heddle.client.Client
import heddle.error.HeddleError
import heddle.mcp.apps.{HostPolicy, Origin}
import heddle.mcp.apps.frame.{Frame, RelayMode}
import heddle.mcp.apps.host.*
import heddle.mcp.client.McpClient
import heddle.mcp.protocol.{Implementation, ToolName}
import zio.*
import zio.json.ast.Json

/** Browser host. It opens one session to this server and frames `show_todos`. */
object Main extends ZIOAppDefault:
  private val info = Implementation("todo-host", "0")

  def run = program.catchAll(error => show(error.message))

  private def program: ZIO[Any, HeddleError, Unit] =
    ZIO
      .scoped {
        for
          client  <- ZIO.service[Client]
          session <- McpClient
            .http(
              s"${dom.window.location.origin}/mcp",
              McpClient.Settings(info, handshake = McpClient.Handshake.Session),
            )
            .provideSomeEnvironment[Scope](_.add(client))
          result <- session.callTool(ToolName("show_todos"), Json.Obj())
          host   <- AppsHost.make(HostSettings(info, HostPolicy.open, resourceSubscribe = true))
          server = AppServer(ServerName("todo"), session)
          mounted <- host.mount(server, Launched(ToolName("show_todos"), Json.Obj(), result))
          parent  <- ZIO.succeed(dom.document.body).someOrElseZIO(ZIO.dieMessage("the host page has no body"))
          origin  <- ZIO
            .fromEither(Origin.from(dom.window.location.origin))
            .orDieWith(e => IllegalArgumentException(e.toString))
          _ <- Frame.mount(mounted, RelayMode.Opaque, parent, origin)
          _ <- ZIO.never
        yield ()
      }
      .provide(
        Client.live,
        HashPins.inMemory,
        Audit.layer(),
        ConsentGate.remembering(_ => ZIO.succeed(ConsentOutcome.AllowForSession)),
      )

  private def show(text: String): UIO[Unit] =
    ZIO.succeed(dom.document.body).someOrElseZIO(ZIO.dieMessage("the host page has no body")).flatMap { body =>
      ZIO.succeed {
        body.textContent = Some(text)
        ()
      }
    }
end Main
