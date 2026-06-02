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

  /** Targeted local search (Löscher-style): hill-climbs a provided `objective` — a branch distance to a hard, uncovered leaf (0 = reached). Keeps the
    * lowest-distance input seen and mutates it (with the multi-scale numeric neighbourhood), with occasional random restart; once the target is
    * reached it explores freely for the other leaves. The objective is scored one draw late — the loop samples the returned `Gen` itself, so we stash
    * the candidate in `pending` and score it on the next call. With no objective it degrades to plain random (constant 0). Holds per-session mutable
    * state; one instance per session, so it isn't shared.
    */
  final class CoverageGuided[A](g: Generatable[A], objective: A => Double) extends Strategy[A]("coverage-guided") {
    private var best: Option[A]    = None
    private var bestDist: Double   = Double.PositiveInfinity
    private var pending: Option[A] = None

    def gen(feedback: SessionFeedback[A]): Gen[A] = {
      pending.foreach { c =>
        val d = objective(c)
        if (d <= bestDist) { best = Some(c); bestDist = d }
      }
      val next = best match {
        case Some(b) if bestDist > 0.0 => Gen.frequency(4 -> g.mutate(b), 1 -> g.arbitrary)
        case _                         => g.arbitrary
      }
      next.map { c => pending = Some(c); c }
    }
  }

  private def mix[A](feedback: SessionFeedback[A], fresh: Gen[A], mutate: A => Gen[A]): Gen[A] =
    if (feedback.seeds.isEmpty) fresh
    else Gen.frequency(1 -> fresh, 1 -> Gen.oneOf(feedback.seeds).flatMap(mutate))

  /** Single source of truth for the registry strategies, simplest → most complex. `coverage-guided` is built separately (it needs an objective), so
    * it's appended to `names` by hand.
    */
  private def all[A: Generatable](pool: ConstantPool): List[Strategy[A]] = {
    val g = Generatable[A]
    List(Random(g), RandomPool(g, pool), MutationGuided(g), MutationGuidedPool(g, pool))
  }

  val names: List[String] = all[Int](ConstantPool.empty).map(_.name) :+ "coverage-guided"

  def parse[A: Generatable](name: String, pool: ConstantPool): Option[Strategy[A]] =
    all[A](pool).find(_.name == name)
}
