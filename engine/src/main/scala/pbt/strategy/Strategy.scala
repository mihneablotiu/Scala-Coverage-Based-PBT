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
  def next[A](context: TacticContext[A]): Gen[A]
}

object Strategy {
  val random: Strategy = new Strategy {
    val name: String = "random"

    def next[A](context: TacticContext[A]): Gen[A] =
      context.generatable.arbitrary
  }

  val pool: Strategy = new Strategy {
    val name: String = "pool"

    def next[A](context: TacticContext[A]): Gen[A] =
      pooled(context).fold(context.generatable.arbitrary)(poolGen => Gen.frequency(1 -> context.generatable.arbitrary, 1 -> poolGen))
  }

  val mutation: Strategy = new Strategy {
    val name: String = "mutation"

    def next[A](context: TacticContext[A]): Gen[A] =
      guided(context, mutated(context))
  }

  val targeted: Strategy = new Strategy {
    val name: String                    = "targeted"
    override val usesTargeting: Boolean = true

    def next[A](context: TacticContext[A]): Gen[A] =
      guided(context, targetedGen(context))
  }

  val poolMutation: Strategy = new Strategy {
    val name: String = "pool-mutation"

    def next[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context), mutated(context))
  }

  val poolTargeted: Strategy = new Strategy {
    val name: String                    = "pool-targeted"
    override val usesTargeting: Boolean = true

    def next[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context), targetedGen(context))
  }

  val mutationTargeted: Strategy = new Strategy {
    val name: String                    = "mutation-targeted"
    override val usesTargeting: Boolean = true

    def next[A](context: TacticContext[A]): Gen[A] =
      guided(context, mutated(context), targetedGen(context))
  }

  val poolMutationTargeted: Strategy = new Strategy {
    val name: String                    = "pool-mutation-targeted"
    override val usesTargeting: Boolean = true

    def next[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context), mutated(context), targetedGen(context))
  }

  val all: List[Strategy] = List(random, pool, mutation, targeted, poolMutation, poolTargeted, mutationTargeted, poolMutationTargeted)
  val names: List[String] = all.map(_.name)
  def byName(name: String): Option[Strategy] = all.find(_.name == name)

  private def guided[A](context: TacticContext[A], generators: Option[Gen[A]]*): Gen[A] =
    generators.flatten.toList match {
      case Nil        => context.generatable.arbitrary
      case gen :: Nil => Gen.frequency(1 -> context.generatable.arbitrary, 2 -> gen)
      case many       => Gen.frequency(((1 -> context.generatable.arbitrary) :: many.map(gen => 1 -> gen)): _*)
    }

  private def pooled[A](context: TacticContext[A]): Option[Gen[A]] =
    Option
      .when(!context.pool.isEmpty && context.hasUncoveredBranches)(context.pool)
      .flatMap(context.generatable.pooled)
      .map(_.flatMap(unseenOrRandom(context)))

  private def mutated[A](context: TacticContext[A]): Option[Gen[A]] =
    context.feedback.corpus.lastOption.map { latest =>
      val older = context.feedback.corpus.dropRight(1)
      val seed  =
        if (older.isEmpty) Gen.const(latest)
        else Gen.frequency(4 -> Gen.const(latest), 1 -> Gen.oneOf(older))
      seed.flatMap(context.generatable.mutate).flatMap(unseenOrRandom(context))
    }

  private def targetedGen[A](context: TacticContext[A]): Option[Gen[A]] =
    context.targetGoals
      .filterNot(goal => context.feedback.coveredAt.contains(goal.coverageId))
      .flatMap(goal => context.feedback.targeted.get(goal.id).map(goal -> _))
      .sortBy { case (_, attempt) => (attempt.distance == 0, attempt.distance) }
      .headOption
      .flatMap { case (goal, attempt) =>
        context.generatable.targeted(attempt.input, goal).map(_.flatMap(unseenOrRandom(context)))
      }

  private def unseenOrRandom[A](context: TacticContext[A])(input: A): Gen[A] =
    if (context.feedback.seenInputs.contains(input)) context.generatable.arbitrary
    else Gen.const(input)
}
