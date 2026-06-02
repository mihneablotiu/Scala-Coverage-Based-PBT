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

  /** 50/50 fresh draw vs mutated seed once the corpus is non-empty, pure random otherwise. Picks the seed to mutate uniformly.
    */
  final case class MutationGuided[A](g: Generatable[A]) extends Strategy[A]("mutation-guided") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, g.arbitrary, g.mutate, uniform[A] _)
  }

  /** Mutation-guided with pool injection on the fresh-draw arm. */
  final case class MutationGuidedPool[A](g: Generatable[A], override val pool: ConstantPool) extends Strategy[A]("mutation-guided-pool") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, g.pooled(pool), g.mutate, uniform[A] _)
  }

  /** Like `mutation-guided`, but spends more mutation budget on the *frontier*: the seed picked to mutate is weighted by discovery rank (later finds
    * get more energy). This is our take on AFLFast's energy schedule — since scoverage records first-fire, not hit counts, we use "how late a seed
    * was discovered" as the rarity proxy instead of path frequency.
    */
  final case class MutationGuidedEnergy[A](g: Generatable[A]) extends Strategy[A]("mutation-guided-energy") {
    def gen(feedback: SessionFeedback[A]): Gen[A] = mix(feedback, g.arbitrary, g.mutate, energy[A] _)
  }

  /** 50/50 fresh vs a mutated seed; `pickSeed` chooses which seed from a non-empty corpus. */
  private def mix[A](feedback: SessionFeedback[A], fresh: Gen[A], mutate: A => Gen[A], pickSeed: Vector[A] => Gen[A]): Gen[A] =
    if (feedback.seeds.isEmpty) fresh
    else Gen.frequency(1 -> fresh, 1 -> pickSeed(feedback.seeds).flatMap(mutate))

  private def uniform[A](seeds: Vector[A]): Gen[A] = Gen.oneOf(seeds)

  /** Weight by discovery rank: the j-th seed found gets weight j+1, so recent frontier seeds are mutated more often (e.g. with 8 seeds the latest is
    * drawn ~22% vs a uniform 12.5%).
    */
  private def energy[A](seeds: Vector[A]): Gen[A] =
    Gen.frequency(seeds.zipWithIndex.map { case (s, j) => (j + 1) -> Gen.const(s) }: _*)

  /** Single source of truth, simplest → most complex. `names` and `parse` derive from it, so adding a strategy is one entry here (plus the same name
    * in the Makefile's STRATEGIES).
    */
  private def all[A: Generatable](pool: ConstantPool): List[Strategy[A]] = {
    val g = Generatable[A]
    List(Random(g), RandomPool(g, pool), MutationGuided(g), MutationGuidedPool(g, pool), MutationGuidedEnergy(g))
  }

  val names: List[String] = all[Int](ConstantPool.empty).map(_.name)

  def parse[A: Generatable](name: String, pool: ConstantPool): Option[Strategy[A]] =
    all[A](pool).find(_.name == name)
}
