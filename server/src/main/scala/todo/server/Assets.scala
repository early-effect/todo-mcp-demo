package todo.server

import heddle.{ServerError as _, *}
import zio.*

/** The pages and scripts the browser loads. Not part of the board API, and not MCP tools. */
object Assets:
  val page: Endpoint[Unit, Nothing, Html] =
    Endpoint.get(PathCodec.empty).out[Html]

  val host: Endpoint[Unit, Nothing, Html] =
    Endpoint.get("host").out[Html]

  val pageJs: Endpoint[Unit, Nothing, Javascript] =
    Endpoint.get("page.js").out[Javascript]

  val hostJs: Endpoint[Unit, Nothing, Javascript] =
    Endpoint.get("host.js").out[Javascript]

  def api(page: Html, host: Html, pageScript: Javascript, hostScript: Javascript): Api[Any] =
    Api("Assets", "0")
      .bind(this.page)(_ => ZIO.succeed(page))
      .bind(this.host)(_ => ZIO.succeed(host))
      .bind(pageJs)(_ => ZIO.succeed(pageScript))
      .bind(hostJs)(_ => ZIO.succeed(hostScript))
end Assets
