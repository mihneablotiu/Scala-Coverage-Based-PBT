package usecase.strategy

import domain.SessionFeedback
import org.scalacheck.{Arbitrary, Gen}

/** **Placeholder** for a corpus + mutation guided strategy (AFL-style).
  *
  * The eventual algorithm will maintain a corpus of past inputs that newly covered something
  * (visible via `feedback.history`'s `newlyCoveredBranches`) and produce next inputs by sampling a
  * corpus entry and applying a type-specific mutation. The `Gen.delay` wrapper ensures the closure
  * re-evaluates each iteration so the latest `feedback` state is in scope.
  *
  * For now the body delegates to `arb.arbitrary`, so this strategy produces the same outputs as
  * [[RandomGen]]; the only observable effect is the per-strategy report folder. When the real
  * algorithm lands, only this file changes.
  */
object MutationGuidedGen {

  def gen[A](feedback: => SessionFeedback[A])(implicit arb: Arbitrary[A]): Gen[A] =
    Gen.delay {
      val _ = feedback // placeholder — real implementation will read feedback.history
      arb.arbitrary
    }
}
