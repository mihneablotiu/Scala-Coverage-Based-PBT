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
  *   - **which property** to assert.
  *
  * Anything else — *where* the source lives, *where* the report is written, what runtime config the
  * engine uses — is the adapter's responsibility (typically set as construction-time constants on
  * the driving adapter implementation).
  *
  * The `[A: Arbitrary]` context bound is the bridge to ScalaCheck: it says "there's a `Gen[A]`
  * resolvable for this type." ScalaCheck auto-derives `Arbitrary` for tuples and most compositions,
  * so a method that takes `(Int, List[Int])` works without extra wiring — `A` is just the tuple.
  */
trait TestRunner {
  def runTests[A: Arbitrary](
      methodName: String,
      strategy: Strategy
  )(property: A => Boolean): IO[Unit]
}
