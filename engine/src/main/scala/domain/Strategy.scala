package domain

import org.scalacheck.{Arbitrary, Gen}

/** How the fuzz loop picks each input, parameterised by the input type `A`.
  *
  * Each case is a concrete class carrying **exactly the resources it needs** — `Random` only holds
  * an `Arbitrary[A]`; `MutationGuided` holds an `Arbitrary[A]` plus a `Mutator[A]`. The trait
  * itself has no type-class bounds, so adding a strategy with a different requirement (e.g.
  * `Enumerate[A]` for bounded-exhaustive) doesn't ripple a bound through everything else.
  *
  * The `gen(feedback)` method takes the running [[SessionFeedback]] explicitly so coverage-guided
  * cases can read the past (seeds, growth) when picking the next input. The plumbing — calling
  * `Gen.delay(strategy.gen(feedback))` on every iteration — lives in `TestRunnerHandler`, so
  * strategies just describe "given what's happened so far, what's the next `Gen[A]`?"
  *
  * The `name` doubles as a report-folder segment: reports land in
  * `engine/reports/<SourceStem>/<method>/<strategy.name>/`.
  */
sealed trait Strategy[A] {
  def name: String
  def gen(feedback: SessionFeedback[A]): Gen[A]
}

object Strategy {

  /** Uniform random sampling from `Arbitrary[A]`. The baseline every guided strategy is compared
    * against — it ignores `feedback` entirely.
    */
  final case class Random[A](arb: Arbitrary[A]) extends Strategy[A] {
    val name = "random"
    def gen(feedback: SessionFeedback[A]): Gen[A] = arb.arbitrary
  }

  /** FuzzChick-flavoured mutation-guided sampling.
    *
    *   - **Seeds** are the inputs whose iteration produced at least one newly covered branch — the
    *     corpus grows monotonically; we never evict, mirroring AFL / FuzzChick (every seed stays a
    *     potential parent for the rest of the session).
    *   - **50 / 50 mutate-vs-fresh** (`Gen.frequency(5, 5)`): the random half keeps the search
    *     ergodic so it can rediscover branches the corpus alone wouldn't drift towards; the mutate
    *     half exploits what the corpus already knows. With no seeds yet, falls through to uniform
    *     random.
    *   - Mutation uses the [[Mutator]] for `A` to produce a "nearby" variant of a uniformly chosen
    *     seed. The next-iteration feedback tells us whether the variant covered something new
    *     (becoming a seed in turn) or didn't (ignored).
    */
  final case class MutationGuided[A](arb: Arbitrary[A], mut: Mutator[A]) extends Strategy[A] {
    val name = "mutation-guided"
    def gen(feedback: SessionFeedback[A]): Gen[A] = {
      val seeds = feedback.history.collect {
        case r if r.newlyCoveredBranches.nonEmpty => r.input
      }
      if (seeds.isEmpty) arb.arbitrary
      else
        Gen.frequency(
          5 -> arb.arbitrary,
          5 -> Gen.oneOf(seeds).flatMap(mut.mutate)
        )
    }
  }

  /** Canonical strategy names, in CLI / report order. Single source of truth for the CLI parser and
    * the Makefile's `STRATEGIES` list — a strategy that isn't here can't be reached from the CLI.
    */
  val names: List[String] = List("random", "mutation-guided")

  /** Materialise a strategy by name for a specific input type `A`. The implicit bounds are the
    * *union* of what any strategy could need — the CLI doesn't know which one will be picked until
    * runtime, so every call site has to be able to construct any of them. The per-case classes
    * still only hold the resources they actually use.
    */
  def parse[A: Arbitrary: Mutator](name: String): Option[Strategy[A]] = name match {
    case "random"          => Some(Random(implicitly))
    case "mutation-guided" => Some(MutationGuided(implicitly, implicitly))
    case _                 => None
  }
}
