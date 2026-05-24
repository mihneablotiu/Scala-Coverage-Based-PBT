package usecase.strategy

import domain.SessionFeedback
import org.scalacheck.{Arbitrary, Gen}

/** Uniform random generation via ScalaCheck's `Arbitrary[A]`. Ignores `feedback` — this is the
  * baseline strategy every guided variant is compared against.
  */
object RandomGen {

  def gen[A](feedback: => SessionFeedback[A])(implicit arb: Arbitrary[A]): Gen[A] = {
    val _ = feedback // intentionally unused; uniform signature with the guided variants
    arb.arbitrary
  }
}
