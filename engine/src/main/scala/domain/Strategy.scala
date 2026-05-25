package domain

/** How the fuzz loop picks each input.
  *
  * The `name` of each strategy doubles as a report-folder segment: every benchmark run under a
  * given strategy writes to `engine/reports/<SourceStem>/<method>/<strategy.name>/`.
  *
  *   - [[Strategy.Random]] — uses `Arbitrary[A].arbitrary`, ignores feedback.
  *   - [[Strategy.MutationGuided]] — *placeholder*. Reserved for a corpus + mutation algorithm
  *     (AFL-style). The placeholder currently delegates to random.
  *   - [[Strategy.FeedbackBiasGuided]] — *placeholder*. Reserved for a feedback-aware custom `Gen`
  *     that biases sampling based on coverage signal. The placeholder currently delegates to
  *     random.
  *
  * **Adding a new strategy** is mechanical: one new `case object` here with a unique `name`, one
  * new entry in [[Strategy.all]], one new `usecase/strategy/<Name>Gen.scala` module exposing
  * `gen[A](feedback)`, one new arm in `TestRunnerHandler.runScalaCheck`'s match, and the new name
  * in the `STRATEGIES` list in the Makefile. The sealed-trait + exhaustive match means a missing
  * wire-up is a compile error, not a runtime surprise.
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

  /** All known strategies, in the order benchmarks should iterate them when a single composition
    * root runs more than one. Single source of truth for `Strategy.parse` and any external
    * orchestrator (`Makefile`'s `STRATEGIES` list, tests, …).
    */
  val all: List[Strategy] = List(Random, MutationGuided, FeedbackBiasGuided)

  /** Resolve a strategy by its `name`, or `None` if no case matches. Case-sensitive — names are
    * meant to round-trip through file-system paths and CLI args.
    */
  def parse(name: String): Option[Strategy] = all.find(_.name == name)
}
