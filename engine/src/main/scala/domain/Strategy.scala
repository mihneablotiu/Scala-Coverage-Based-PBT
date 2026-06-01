package domain

import org.scalacheck.Gen

/** Four named ways to pick the next input, expressed as one-liners over a [[Generatable]]. What each strategy *does* — random, pool, mutate,
  * pool+mutate — is visible at the strategy boundary, not hidden behind injected type classes.
  */
sealed abstract class Strategy[A](val name: String) {
  def gen(feedback: SessionFeedback[A]): Gen[A]

  /** The literals this strategy injects — empty unless it's a `*-pool` variant. */
  def pool: ConstantPool = ConstantPool.empty
}

object Strategy {

  /** Uniform random — ignores `feedback`. Identical to a plain ScalaCheck `Arbitrary[A]` draw. */
  final case class Random[A](g: Generatable[A]) extends Strategy[A]("random") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = g.arbitrary
  }

  /** Random with literal injection: each draw mixes mined source values into the base. */
  final case class RandomPool[A](g: Generatable[A], override val pool: ConstantPool) extends Strategy[A]("random-pool") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = g.pooled(pool)
  }

  /** FuzzChick-style mutation: 50/50 fresh draw vs mutated seed when the corpus is non-empty, pure random otherwise. */
  final case class MutationGuided[A](g: Generatable[A]) extends Strategy[A]("mutation-guided") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, g.arbitrary, g.mutate)
  }

  /** Mutation-guided with pool injection on the fresh-draw arm. */
  final case class MutationGuidedPool[A](g: Generatable[A], override val pool: ConstantPool) extends Strategy[A]("mutation-guided-pool") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, g.pooled(pool), g.mutate)
  }

  private def mix[A](feedback: SessionFeedback[A], fresh: Gen[A], mutate: A => Gen[A]): Gen[A] = {
    val seeds = feedback.seeds
    if (seeds.isEmpty) fresh
    else Gen.frequency(1 -> fresh, 1 -> Gen.oneOf(seeds).flatMap(mutate))
  }

  /** Canonical CLI / report order, simplest → most complex. Aligns with `STRATEGIES` in the Makefile. */
  val names: List[String] = List("random", "random-pool", "mutation-guided", "mutation-guided-pool")

  /** Build the strategy whose `name` matches; the pool is ignored by the non-pool variants. */
  def parse[A: Generatable](name: String, pool: ConstantPool): Option[Strategy[A]] = name match {
    case "random"               => Some(Random(Generatable[A]))
    case "random-pool"          => Some(RandomPool(Generatable[A], pool))
    case "mutation-guided"      => Some(MutationGuided(Generatable[A]))
    case "mutation-guided-pool" => Some(MutationGuidedPool(Generatable[A], pool))
    case _                      => None
  }
}
