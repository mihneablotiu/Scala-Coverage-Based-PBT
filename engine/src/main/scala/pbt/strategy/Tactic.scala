package pbt.strategy

import org.scalacheck.Gen
import pbt.{Feedback, Pos}
import pbt.analysis.{BranchTree, ParsedMethod, Predicate}
import pbt.gen.{ConstantPool, Generatable}

/** The one shape every coverage-guided tactic follows: given the live [[Feedback]], optionally `propose` how to draw the next input (`None` =
  * "nothing to add right now"); `observe` lets a stateful tactic learn from the input that was just run. A [[Strategy]] is just a set of these mixed
  * with a plain random draw — so `random` is the empty set, i.e. stock ScalaCheck.
  *
  * To add a tactic: implement this trait, add a [[Kind]], wire it in [[of]]. Nothing else changes.
  */
trait Tactic[A] {
  def propose(fb: Feedback[A]): Option[Gen[A]]
  def observe(input: A, fb: Feedback[A]): Unit = ()
}

object Tactic {

  sealed trait Kind
  case object Pool     extends Kind // inject literals uncovered branches need
  case object Mutation extends Kind // perturb a coverage-growing seed
  case object Gradient extends Kind // hill-climb branch distance to the nearest uncovered leaf

  def of[A](kind: Kind, g: Generatable[A], pm: ParsedMethod): Tactic[A] = kind match {
    case Pool     => new PoolTactic(g, BranchTree.leafLiterals(pm.tree))
    case Mutation => new MutationTactic(g)
    case Gradient => new GradientTactic(g, BranchTree.leafPaths(pm.tree), pm.paramCount)
  }

  /** Inject the literals that still-uncovered leaves need (DRAGEN²-style mining, coverage-filtered). As leaves are covered, their literals retire. */
  private final class PoolTactic[A](g: Generatable[A], leafLiterals: Map[Pos, ConstantPool]) extends Tactic[A] {
    def propose(fb: Feedback[A]): Option[Gen[A]] = {
      val active = leafLiterals.iterator.collect { case (pos, pool) if !fb.covered(pos) => pool }.foldLeft(ConstantPool.empty)(_ ++ _)
      Option.when(!active.isEmpty)(g.pooled(active))
    }
  }

  /** Perturb a corpus seed — an input that previously grew coverage (FuzzChick / AFL). */
  private final class MutationTactic[A](g: Generatable[A]) extends Tactic[A] {
    def propose(fb: Feedback[A]): Option[Gen[A]] =
      Option.when(fb.corpus.nonEmpty)(Gen.oneOf(fb.corpus).flatMap(g.mutate))
  }

  /** Hill-climb the branch distance to the nearest uncovered leaf (Korel / targeted PBT): keep the closest input seen, then mutate it. */
  private final class GradientTactic[A](g: Generatable[A], leafPaths: Map[Pos, List[Predicate.Cond]], paramCount: Int) extends Tactic[A] {
    private var best: Option[A] = None

    def propose(fb: Feedback[A]): Option[Gen[A]] = {
      val ts = targets(fb)
      best.filter(b => ts.nonEmpty && fitness(b, ts).exists(_ > 0.0)).map(g.mutate)
    }

    override def observe(input: A, fb: Feedback[A]): Unit = {
      val ts = targets(fb)
      val fc = fitness(input, ts)
      if (fc.isDefined && best.flatMap(fitness(_, ts)).forall(fc.get <= _)) best = Some(input)
    }

    private def targets(fb: Feedback[A]): List[List[Predicate.Cond]] =
      leafPaths.iterator.collect { case (pos, guards) if !fb.covered(pos) => guards }.toList

    private def fitness(input: A, ts: List[List[Predicate.Cond]]): Option[Double] =
      ts.flatMap(Predicate.pathFitness(_, Predicate.bind(input, paramCount))).minOption
  }
}
