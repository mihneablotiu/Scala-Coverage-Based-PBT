package domain

/** How the fuzz loop picks each input.
  *
  * The `name` of each strategy doubles as a report-folder segment: every benchmark run under a
  * given strategy writes to `engine/reports/<SourceStem>/<method>/<strategy.name>/`.
  *
  *   - [[Strategy.Random]] — uses `Arbitrary[A].arbitrary`, ignores feedback.
  *   - [[Strategy.MutationGuided]] — *placeholder*. Reserved for a corpus + mutation algorithm
  *     (AFL-style). The placeholder currently delegates to random.
  *   - [[Strategy.FeedbackBiasGuided]] — *placeholder*. Reserved for a feedback-aware custom
  *     `Gen` that biases sampling based on coverage signal. The placeholder currently delegates
  *     to random.
  *
  * **Adding a new strategy** is mechanical: one new `case object` here with a unique `name`, one
  * new `usecase/strategy/<Name>Gen.scala` module exposing `gen[A](feedback)`, one new arm in
  * `TestRunnerHandler.runScalaCheck`'s match, and one new entry in `Main.strategies`. The
  * sealed-trait + exhaustive match means a missing wire-up is a compile error, not a runtime
  * surprise.
  */
sealed trait Strategy {
  def name: String
}

object Strategy {
  case object Random extends Strategy {
    val name = "random"
  }

  case object MutationGuided extends Strategy {
    val name = "mutation-guided"
  }

  case object FeedbackBiasGuided extends Strategy {
    val name = "feedback-bias-guided"
  }
}
