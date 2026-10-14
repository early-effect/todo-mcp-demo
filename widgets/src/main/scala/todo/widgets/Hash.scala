package todo.widgets

import ascent.dom
import ascent.squawk.{Squawk, sq}
import zio.*
import zio.stream.ZStream

import scala.scalajs.js

/** The page fragment, as the filter the widgets already understand. The browser is the source of truth. */
object Hash:

  def filter: URIO[Scope, Squawk[todo.Filter]] =
    for
      source <- sq(current)
      _      <- ZStream
        .asyncScoped[Any, Nothing, Unit] { emit =>
          val listener: js.Function1[dom.Event, Unit] =
            (_: dom.Event) => emit(ZIO.succeed(Chunk(())))
          ZIO.acquireRelease(
            ZIO.succeed(dom.window.addEventListener("hashchange", listener))
          )(_ => ZIO.succeed(dom.window.removeEventListener("hashchange", listener)))
        }
        .foreach(_ => source.set(current))
        .forkScoped
    yield source

  private def current: todo.Filter = todo.Filter.fromHash(dom.window.location.hash)
end Hash
