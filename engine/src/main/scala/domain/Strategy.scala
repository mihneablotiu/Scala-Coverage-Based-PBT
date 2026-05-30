package domain

import org.scalacheck.{Arbitrary, Gen}

/** How the fuzz loop picks each input.
  *
  * Each case carries only the resources it actually uses (`Random` needs `Arbitrary[A]`, `MutationGuided` also needs `Mutator[A]`), so adding a
  * strategy with a different requirement doesn't ripple a bound through the rest of the engine. `gen(feedback)` takes the running [[SessionFeedback]]
  * explicitly so coverage-guided cases can read the past when picking the next input. The `name` doubles as a report-folder segment.
  */
sealed trait Strategy[A] {
  def name: String
  def gen(feedback: SessionFeedback[A]): Gen[A]
}

object Strategy {

  /** Uniform random sampling — the baseline that ignores `feedback`. */
  final case class Random[A](arb: Arbitrary[A]) extends Strategy[A] {
    val name                                      = "random"
    def gen(feedback: SessionFeedback[A]): Gen[A] = arb.arbitrary
  }

  /** FuzzChick-style mutation. Seeds are inputs whose iteration newly covered a branch — the corpus only grows; the cached corpus
    * lives in [[SessionFeedback.seeds]] so this `gen` is O(1) per call. With seeds available, 50/50 between fresh `Arbitrary[A]`
    * draws (keeps the search ergodic) and mutating a uniformly chosen seed; with no seeds yet, pure random.
    */
  final case class MutationGuided[A](arb: Arbitrary[A], mut: Mutator[A]) extends Strategy[A] {
    val name                                      = "mutation-guided"
    def gen(feedback: SessionFeedback[A]): Gen[A] = {
      val seeds = feedback.seeds
      if (seeds.isEmpty) arb.arbitrary
      else
        Gen.frequency(
          5 -> arb.arbitrary,
          5 -> Gen.oneOf(seeds).flatMap(mut.mutate)
        )
    }
  }

  /** Canonical CLI / report order. Must stay aligned with `STRATEGIES` in the Makefile. */
  val names: List[String] = List("random", "mutation-guided")

  /** Resolve a strategy by name. The implicit bounds are the union of what any case might need; each case still only stores what it uses.
    */
  def parse[A: Arbitrary: Mutator](name: String): Option[Strategy[A]] = name match {
    case "random"          => Some(Random(implicitly))
    case "mutation-guided" => Some(MutationGuided(implicitly, implicitly))
    case _                 => None
  }
}
