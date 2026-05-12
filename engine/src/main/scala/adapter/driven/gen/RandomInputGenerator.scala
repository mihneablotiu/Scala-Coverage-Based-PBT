package adapter.driven.gen

import cats.effect.IO
import domain.SessionFeedback
import org.scalacheck.{rng, Gen}
import port.driven.InputGenerator

/** Uniform `Int` generator backed by ScalaCheck. Ignores feedback — this is the no-information
  * baseline that any guided strategy is compared against.
  */
object RandomInputGenerator {

  private val IntGen: Gen[Int] = Gen.chooseNum(Int.MinValue, Int.MaxValue)

  def apply(initialSeed: Long): InputGenerator = new InputGenerator {
    private var seed: rng.Seed = rng.Seed(initialSeed)

    override def next(feedback: SessionFeedback): IO[Int] = IO {
      val value = IntGen.pureApply(Gen.Parameters.default, seed)
      seed = seed.next
      value
    }
  }
}
