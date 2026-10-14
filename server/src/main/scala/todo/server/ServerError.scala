package todo.server

import heddle.mcp.McpBuildError
import heddle.mcp.apps.AppBuildError
import zio.NonEmptyChunk

/** Why this process cannot serve the board. */
enum ServerError(val message: String):
  case Missing(name: String)                      extends ServerError(s"$name is missing")
  case Empty(name: String)                        extends ServerError(s"$name is empty")
  case Unreadable(name: String, cause: Throwable) extends ServerError(s"$name could not be read: $cause")
  case Mcp(errors: NonEmptyChunk[McpBuildError])  extends ServerError(errors.map(_.message).mkString("; "))
  case App(errors: NonEmptyChunk[AppBuildError])  extends ServerError(errors.map(_.message).mkString("; "))
  case Serve(cause: heddle.error.ServerError)     extends ServerError(cause.message)

  override def toString: String = message
end ServerError
