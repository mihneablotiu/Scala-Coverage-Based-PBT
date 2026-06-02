package domain

import org.scalacheck.Gen

/** A named way to pick the next input, expressed as a one-liner over a [[Generatable]]. */
sealed abstract class Strategy[A](val name: String) {
  def gen(feedback: SessionFeedback[A]): Gen[A]

  /** Literals this strategy injects — empty unless it's a `*-pool` variant. */
  def pool: ConstantPool = ConstantPool.empty
}

object Strategy {

  /** Uniform random — ignores feedback. Identical to a plain ScalaCheck `Arbitrary[A]` draw. */
  final case class Random[A](g: Generatable[A]) extends Strategy[A]("random") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = g.arbitrary
  }

  /** Random with literal injection: each draw mixes mined source values into the base. */
  final case class RandomPool[A](g: Generatable[A], override val pool: ConstantPool) extends Strategy[A]("random-pool") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = g.pooled(pool)
  }

  /** 50/50 fresh draw vs mutated seed once the corpus is non-empty, pure random otherwise. */
  final case class MutationGuided[A](g: Generatable[A]) extends Strategy[A]("mutation-guided") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, g.arbitrary, g.mutate)
  }

  /** Mutation-guided with pool injection on the fresh-draw arm. */
  final case class MutationGuidedPool[A](g: Generatable[A], override val pool: ConstantPool) extends Strategy[A]("mutation-guided-pool") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, g.pooled(pool), g.mutate)
  }

  /** Like `mutation-guided`, but the fresh/mutate split *adapts*: the longer we go without new coverage (`feedback.stall`), the more we favour a
    * fresh draw — so mutation backs off when it isn't paying off and stays active right after a discovery. Fixes the case where a fixed 50/50 split
    * lets mutation waste budget and fall *below* random (e.g. `luhnCheck`).
    */
  final case class MutationGuidedAdaptive[A](g: Generatable[A]) extends Strategy[A]("mutation-guided-adaptive") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = adaptive(feedback, g.arbitrary, g.mutate)
  }

  private def mix[A](feedback: SessionFeedback[A], fresh: Gen[A], mutate: A => Gen[A]): Gen[A] =
    if (feedback.seeds.isEmpty) fresh
    else Gen.frequency(1 -> fresh, 1 -> Gen.oneOf(feedback.seeds).flatMap(mutate))

  /** Fresh weight grows with the stall (1 + stall/64, capped at 32); mutate weight stays 1. So the split is ~50/50 right after a discovery and decays
    * toward almost-pure-fresh during a long drought.
    */
  private def adaptive[A](feedback: SessionFeedback[A], fresh: Gen[A], mutate: A => Gen[A]): Gen[A] =
    if (feedback.seeds.isEmpty) fresh
    else {
      val freshWeight = math.min(1 + feedback.stall / 64, 32)
      Gen.frequency(freshWeight -> fresh, 1 -> Gen.oneOf(feedback.seeds).flatMap(mutate))
    }

  /** Single source of truth, simplest → most complex. `names` and `parse` derive from it, so adding a strategy is one entry here (plus the same name
    * in the Makefile's STRATEGIES).
    */
  private def all[A: Generatable](pool: ConstantPool): List[Strategy[A]] = {
    val g = Generatable[A]
    List(Random(g), RandomPool(g, pool), MutationGuided(g), MutationGuidedPool(g, pool), MutationGuidedAdaptive(g))
  }

  val names: List[String] = all[Int](ConstantPool.empty).map(_.name)

  def parse[A: Generatable](name: String, pool: ConstantPool): Option[Strategy[A]] =
    all[A](pool).find(_.name == name)
}
