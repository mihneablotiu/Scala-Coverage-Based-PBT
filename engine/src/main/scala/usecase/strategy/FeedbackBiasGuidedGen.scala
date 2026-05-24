package usecase.strategy

import domain.SessionFeedback
import org.scalacheck.{Arbitrary, Gen}

/** **Placeholder** for a feedback-aware custom-`Gen` strategy.
  *
  * The eventual algorithm will inspect `feedback.coveredBranches` and `feedback.growthCurve` to
  * bias sampling — for instance, weighting the size schedule toward sizes that recently produced
  * new coverage, or biasing element distributions toward values seen in inputs that hit new
  * arms. The `Gen.delay` wrapper ensures the closure re-evaluates each iteration so the latest
  * `feedback` state is in scope.
  *
  * For now the body delegates to `arb.arbitrary`, so this strategy produces the same outputs as
  * [[RandomGen]]; the only observable effect is the per-strategy report folder. When the real
  * algorithm lands, only this file changes.
  */
object FeedbackBiasGuidedGen {

  def gen[A](feedback: => SessionFeedback[A])(implicit arb: Arbitrary[A]): Gen[A] =
    Gen.delay {
      val _ = feedback // placeholder — real implementation will read feedback.coveredBranches
      arb.arbitrary
    }
}
