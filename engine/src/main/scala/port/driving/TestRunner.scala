package port.driving

import cats.effect.IO
import domain.Strategy
import org.scalacheck.Gen

import java.nio.file.Path

/** Drives one full fuzz session against a single SUT method.
  *
  * Parameterised over the input type `A`. The caller supplies a ScalaCheck `Gen[A]` from which the
  * random (or guided) strategy will draw values.
  */
trait TestRunner {
  def run[A](
      sourceFile: Path,
      methodName: String,
      property: A => Boolean,
      strategy: Strategy,
      gen: Gen[A],
      outDir: Path
  ): IO[Unit]
}
