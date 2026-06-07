package pbt.strategy

import org.scalacheck.Gen
import pbt.Coverage
import pbt.gen.{ConstantPool, Generatable}
import pbt.targeting.BranchGoal

final case class TacticContext[A](
    generatable: Generatable[A],
    feedback: Feedback[A],
    targets: List[Coverage.StatementTarget],
    pool: ConstantPool,
    targetGoals: List[BranchGoal]
) {
  def hasUncoveredBranches: Boolean =
    targets.exists(target => target.branch && !feedback.coveredAt.contains(target.id))
}

sealed trait Strategy {
  def name: String
  def usesTargeting: Boolean = false

  // Guided strategies redraw a value already executed so no budget is wasted re-running a duplicate.
  // The random baseline opts out, so it stays bit-for-bit stock ScalaCheck and remains an
  // unimpeachable point of comparison.
  protected def deduplicates: Boolean = true

  // `propose` mixes the chosen tactics with the random baseline at equal weight; guided strategies
  // then redraw until the value has not been executed before.
  final def next[A](context: TacticContext[A]): Gen[A] = {
    val proposed = propose(context)
    if (deduplicates) Strategy.fresh(context, proposed) else proposed
  }

  protected def propose[A](context: TacticContext[A]): Gen[A]
}

object Strategy {
  // Bounded re-draws so a value already executed is replaced by a fresh draw; a handful suffices on
  // any real domain, and we give up afterwards so an exhausted small domain (e.g. Boolean) neither
  // loops forever nor wastes draws it can never satisfy.
  private val FreshDrawAttempts = 20

  val random: Strategy = new Strategy {
    val name: String                             = "random"
    override protected def deduplicates: Boolean = false

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      context.generatable.arbitrary
  }

  val pool: Strategy = new Strategy {
    val name: String = "pool"

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context))
  }

  val mutation: Strategy = new Strategy {
    val name: String = "mutation"

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      guided(context, mutated(context))
  }

  val targeted: Strategy = new Strategy {
    val name: String                    = "targeted"
    override val usesTargeting: Boolean = true

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      guided(context, targetedGen(context))
  }

  val poolMutation: Strategy = new Strategy {
    val name: String = "pool-mutation"

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context), mutated(context))
  }

  val poolTargeted: Strategy = new Strategy {
    val name: String                    = "pool-targeted"
    override val usesTargeting: Boolean = true

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context), targetedGen(context))
  }

  val mutationTargeted: Strategy = new Strategy {
    val name: String                    = "mutation-targeted"
    override val usesTargeting: Boolean = true

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      guided(context, mutated(context), targetedGen(context))
  }

  val poolMutationTargeted: Strategy = new Strategy {
    val name: String                    = "pool-mutation-targeted"
    override val usesTargeting: Boolean = true

    protected def propose[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context), mutated(context), targetedGen(context))
  }

  val all: List[Strategy] = List(random, pool, mutation, targeted, poolMutation, poolTargeted, mutationTargeted, poolMutationTargeted)
  val names: List[String] = all.map(_.name)
  def byName(name: String): Option[Strategy] = all.find(_.name == name)

  // The random baseline and every active tactic share the draw at equal weight: a combination of N
  // sources is mixed 1:1(:…:1), so no single source dominates the others.
  private def guided[A](context: TacticContext[A], generators: Option[Gen[A]]*): Gen[A] =
    generators.flatten.toList match {
      case Nil  => context.generatable.arbitrary
      case live => Gen.frequency(((1 -> context.generatable.arbitrary) :: live.map(gen => 1 -> gen)): _*)
    }

  private def pooled[A](context: TacticContext[A]): Option[Gen[A]] =
    Option
      .when(!context.pool.isEmpty && context.hasUncoveredBranches)(context.pool)
      .flatMap(context.generatable.pooled)

  private def mutated[A](context: TacticContext[A]): Option[Gen[A]] =
    context.feedback.corpus.lastOption.map { latest =>
      val older = context.feedback.corpus.dropRight(1)
      val seed  =
        if (older.isEmpty) Gen.const(latest)
        else Gen.frequency(4 -> Gen.const(latest), 1 -> Gen.oneOf(older))
      seed.flatMap(context.generatable.mutate)
    }

  private def targetedGen[A](context: TacticContext[A]): Option[Gen[A]] =
    context.targetGoals
      .filterNot(goal => context.feedback.coveredAt.contains(goal.coverageId))
      .flatMap(goal => context.feedback.targeted.get(goal.id))
      .sortBy(attempt => (attempt.distance == 0, attempt.distance))
      .headOption
      .map(attempt => context.generatable.mutate(attempt.input))

  // Draw from `gen`, replacing a value already executed with another draw; after a bounded number
  // of attempts accept whatever is drawn so a saturated domain cannot loop forever.
  private def fresh[A](context: TacticContext[A], gen: Gen[A]): Gen[A] =
    freshWithin(context.feedback.seenInputs, gen, FreshDrawAttempts)

  private def freshWithin[A](seen: Set[A], gen: Gen[A], attempts: Int): Gen[A] =
    if (attempts <= 0) gen
    else gen.flatMap(value => if (seen.contains(value)) freshWithin(seen, gen, attempts - 1) else Gen.const(value))
}
