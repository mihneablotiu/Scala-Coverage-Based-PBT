package port.driving

import cats.effect.IO
import domain.Strategy

/** Drives one fuzz session over a SUT method.
  *
  * The caller declares which method to exercise, the [[Strategy]] that picks inputs (it already carries the type-class evidence for its `A`), and the
  * boolean predicate to evaluate per input. Where the source lives and where output is written are adapter concerns set at construction time, so the
  * port stays free of filesystem details.
  *
  * `property` returns `Boolean` so a client can evaluate real behaviour, not just trigger coverage. The engine still measures coverage as the primary
  * observable; how (or whether) the boolean is honoured beyond being called is an implementation choice of the adapter stack.
  */
trait TestRunner {
  def runTests[A](
      methodName: String,
      strategy: Strategy[A]
  )(property: A => Boolean): IO[Unit]
}
