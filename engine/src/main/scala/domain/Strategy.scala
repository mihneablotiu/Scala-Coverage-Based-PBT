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

  /** Autonomous coverage-guided search. From the live coverage it knows which leaves are still uncovered; for each it has a path predicate (from the
    * source), so it hill-climbs the **branch distance** to the *nearest* uncovered leaf — keeping the best input seen and mutating it (with the
    * multi-scale numeric neighbourhood) plus the occasional restart. Leaves whose guard the engine can't express numerically contribute no gradient,
    * so where none applies it just explores randomly. No hand-written objective — the target is derived from the source and the coverage so far.
    *
    * The candidate is scored one draw late (the loop samples the returned `Gen` itself), so we stash it in `pending`. Holds per-session mutable
    * state; one instance per session, so it isn't shared.
    */
  final class CoverageGuided[A](g: Generatable[A], leafPaths: Map[Pos, List[Predicate.Cond]], paramCount: Int)
      extends Strategy[A]("coverage-guided") {

    private var best: Option[A]    = None
    private var pending: Option[A] = None

    def gen(feedback: SessionFeedback[A]): Gen[A] = {
      val targets = leafPaths.collect { case (pos, guards) if !feedback.coveredBranches(pos) => guards }.toList
      pending.foreach(c => if (closerThanBest(c, targets)) best = Some(c))
      val next =
        if (targets.isEmpty) g.arbitrary
        else
          best match {
            case Some(b) if fitness(b, targets).exists(_ > 0.0) => Gen.frequency(4 -> g.mutate(b), 1 -> g.arbitrary)
            case _                                              => g.arbitrary
          }
      next.map { c => pending = Some(c); c }
    }

    private def closerThanBest(c: A, targets: List[List[Predicate.Cond]]): Boolean = {
      val fc = fitness(c, targets)
      fc.isDefined && best.flatMap(fitness(_, targets)).forall(fc.get <= _)
    }

    /** Branch distance to the nearest uncovered target; `None` if none is numerically expressible. */
    private def fitness(input: A, targets: List[List[Predicate.Cond]]): Option[Double] = {
      val args = Predicate.bind(input, paramCount)
      targets.flatMap(Predicate.pathFitness(_, args)).minOption
    }
  }

  private def mix[A](feedback: SessionFeedback[A], fresh: Gen[A], mutate: A => Gen[A]): Gen[A] =
    if (feedback.seeds.isEmpty) fresh
    else Gen.frequency(1 -> fresh, 1 -> Gen.oneOf(feedback.seeds).flatMap(mutate))

  /** Single source of truth for the registry strategies, simplest → most complex. `coverage-guided` is built separately (it needs the parsed leaf
    * paths, not just a `Generatable`), so it's appended to `names` by hand.
    */
  private def all[A: Generatable](pool: ConstantPool): List[Strategy[A]] = {
    val g = Generatable[A]
    List(Random(g), RandomPool(g, pool), MutationGuided(g), MutationGuidedPool(g, pool))
  }

  val names: List[String] = all[Int](ConstantPool.empty).map(_.name) :+ "coverage-guided"

  def parse[A: Generatable](name: String, pool: ConstantPool): Option[Strategy[A]] =
    all[A](pool).find(_.name == name)
}
