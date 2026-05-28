package port.driving

import cats.effect.IO
import domain.Strategy

/** Drives one fuzz session against a single SUT method.
  *
  * Pure use-case interface — **no filesystem, no source paths, no output paths**. The caller of
  * this port only declares:
  *
  *   - **which method** to exercise (by name),
  *   - **which strategy** to drive it with (already materialised for `A`, carrying its own
  *     resources),
  *   - **what to do** with each generated input.
  *
  * Anything else — *where* the source lives, *where* the report is written, what runtime config the
  * engine uses — is the adapter's responsibility (typically set as construction-time constants on
  * the driving adapter implementation).
  *
  * The strategy is `Strategy[A]` rather than a bare `Strategy`: each strategy already carries the
  * type-class evidence it needs (an `Arbitrary[A]`, optionally a `Mutator[A]`, etc.). That keeps
  * this port free of strategy-specific bounds — adding a strategy with new requirements does not
  * change this signature.
  *
  * `exercise` returns `Any` (in practice `Boolean` or `Unit`); its return value is discarded
  * because this framework measures coverage, not behaviour. The session reports "what scoverage
  * recorded" regardless of what the SUT method returned for any given input.
  */
trait TestRunner {
  def runTests[A](
      methodName: String,
      strategy: Strategy[A]
  )(exercise: A => Any): IO[Unit]
}
