package domain

/** How the fuzz loop picks each input.
  *
  *   - [[Strategy.Random]]: uses `Arbitrary[A].arbitrary`, ignores feedback.
  *   - [[Strategy.Guided]]: coverage-guided. Placeholder today — the handler re-evaluates the
  *     generator each iteration through a `Gen.delay` closure that has access to the running
  *     [[SessionFeedback]], then delegates to the random `Arbitrary`. The closure is the hook a
  *     real guided strategy plugs into.
  */
sealed trait Strategy

object Strategy {
  case object Random extends Strategy
  case object Guided extends Strategy
}
