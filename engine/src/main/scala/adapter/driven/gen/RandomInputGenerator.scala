package adapter.driven.gen

import cats.effect.IO
import domain.SessionFeedback
import org.scalacheck.{rng, Gen}
import port.driven.InputGenerator

/** Generic random generator backed by a ScalaCheck `Gen[A]`. Ignores feedback — this is the
  * no-information baseline that any guided strategy is compared against.
  */
object RandomInputGenerator {

  def apply[A](gen: Gen[A], initialSeed: Long): InputGenerator[A] = new InputGenerator[A] {
    private var seed: rng.Seed = rng.Seed(initialSeed)

    override def next(feedback: SessionFeedback[A]): IO[A] = IO {
      val value = gen.pureApply(Gen.Parameters.default, seed)
      seed = seed.next
      value
    }
  }
}
