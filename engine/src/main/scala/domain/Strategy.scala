package domain

import org.scalacheck.{Arbitrary, Gen}

/** Four named ways to pick the next input. Each case spells out its own `gen`, so what the strategy *does* — random, pool, mutate, pool+mutate — is
  * visible at the strategy boundary, not hidden behind an injected `Arbitrary`.
  */
sealed trait Strategy[A] {
  def name: String
  def gen(feedback: SessionFeedback[A]): Gen[A]
}

object Strategy {

  /** Uniform random — ignores `feedback`. */
  final case class Random[A](arb: Arbitrary[A]) extends Strategy[A] {
    val name                                      = "random"
    def gen(feedback: SessionFeedback[A]): Gen[A] = arb.arbitrary
  }

  /** Random with literal injection: every primitive draw mixes mined values into the base via [[Pooled]]. */
  final case class RandomPool[A](base: Arbitrary[A], pool: ConstantPool)(implicit p: Pooled[A]) extends Strategy[A] {
    val name                                      = "random-pool"
    def gen(feedback: SessionFeedback[A]): Gen[A] = p.arb(pool).arbitrary
  }

  /** FuzzChick-style mutation: 50/50 fresh draw vs mutated seed when the corpus is non-empty, pure random otherwise. */
  final case class MutationGuided[A](arb: Arbitrary[A], mut: Mutator[A]) extends Strategy[A] {
    val name                                      = "mutation-guided"
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, arb.arbitrary, mut)
  }

  /** Mutation-guided with pool injection on the fresh-draw arm. */
  final case class MutationGuidedPool[A](base: Arbitrary[A], pool: ConstantPool, mut: Mutator[A])(implicit p: Pooled[A]) extends Strategy[A] {
    val name                                      = "mutation-guided-pool"
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, p.arb(pool).arbitrary, mut)
  }

  private def mix[A](feedback: SessionFeedback[A], fresh: Gen[A], mut: Mutator[A]): Gen[A] = {
    val seeds = feedback.seeds
    if (seeds.isEmpty) fresh
    else Gen.frequency(5 -> fresh, 5 -> Gen.oneOf(seeds).flatMap(mut.mutate))
  }

  /** Canonical CLI / report order, simplest → most complex. Aligns with `STRATEGIES` in the Makefile. */
  val names: List[String] = List("random", "random-pool", "mutation-guided", "mutation-guided-pool")

  /** Build the strategy whose `name` matches the CLI string. The pool is ignored by the non-pool variants. */
  def parse[A: Arbitrary: Mutator: Pooled](name: String, pool: ConstantPool): Option[Strategy[A]] = name match {
    case "random"               => Some(Random(implicitly))
    case "random-pool"          => Some(RandomPool(implicitly, pool))
    case "mutation-guided"      => Some(MutationGuided(implicitly, implicitly))
    case "mutation-guided-pool" => Some(MutationGuidedPool(implicitly, pool, implicitly))
    case _                      => None
  }
}
