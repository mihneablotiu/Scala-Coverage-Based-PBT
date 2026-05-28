package port.driving

import cats.effect.IO
import domain.Strategy
import org.scalacheck.Arbitrary

/** Drives one fuzz session against a single SUT method.
  *
  * Pure use-case interface — **no filesystem, no source paths, no output paths**. The caller of
  * this port only declares:
  *
  *   - **which method** to exercise (by name),
  *   - **which strategy** to drive it with (random / coverage-guided),
  *   - **what to do** with each generated input.
  *
  * Anything else — *where* the source lives, *where* the report is written, what runtime config the
  * engine uses — is the adapter's responsibility (typically set as construction-time constants on
  * the driving adapter implementation).
  *
  * The `[A: Arbitrary]` context bound is the bridge to ScalaCheck: it says "there's a `Gen[A]`
  * resolvable for this type." ScalaCheck auto-derives `Arbitrary` for tuples and most compositions,
  * so a method that takes `(Int, List[Int])` works without extra wiring — `A` is just the tuple.
  *
  * `exercise` returns `Any` (in practice `Boolean` or `Unit`); its return value is discarded
  * because this framework measures coverage, not behaviour. The session reports "what scoverage
  * recorded" regardless of what the SUT method returned for any given input.
  */
trait TestRunner {
  def runTests[A: Arbitrary](
      methodName: String,
      strategy: Strategy
  )(exercise: A => Any): IO[Unit]
}
