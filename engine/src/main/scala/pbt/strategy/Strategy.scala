package pbt.strategy

import org.scalacheck.Gen
import pbt.Coverage
import pbt.gen.{ConstantPool, Generatable}

final case class TacticContext[A](
    generatable: Generatable[A],
    feedback: Feedback[A],
    targets: List[Coverage.StatementTarget],
    pool: ConstantPool
) {
  def uncoveredStatements: List[Coverage.StatementTarget] =
    targets.filterNot(target => feedback.coveredAt.contains(target.id))

  def uncoveredBranches: List[Coverage.StatementTarget] =
    uncoveredStatements.filter(_.branch)

  def hasUncoveredBranches: Boolean = uncoveredBranches.nonEmpty
}

sealed trait Strategy {
  def name: String
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

  val poolMutation: Strategy = new Strategy {
    val name: String = "pool-mutation"

    def next[A](context: TacticContext[A]): Gen[A] =
      guided(context, pooled(context), mutated(context))
  }

  val all: List[Strategy]                    = List(random, pool, mutation, poolMutation)
  val names: List[String]                    = all.map(_.name)
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

  private def unseenOrRandom[A](context: TacticContext[A])(input: A): Gen[A] =
    if (context.feedback.seenInputs.contains(input)) context.generatable.arbitrary
    else Gen.const(input)
}
