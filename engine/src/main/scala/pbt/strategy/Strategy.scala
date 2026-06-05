package pbt.strategy

import org.scalacheck.Gen
import pbt.gen.{ConstantPool, Generatable}

final case class Strategy(name: String, tactics: List[Tactic])

object Strategy {
  val random: Strategy       = Strategy("random", Nil)
  val pool: Strategy         = Strategy("pool", List(Tactic.Pool))
  val mutation: Strategy     = Strategy("mutation", List(Tactic.Mutation))
  val poolMutation: Strategy = Strategy("pool-mutation", List(Tactic.Pool, Tactic.Mutation))

  val all: List[Strategy]                    = List(random, pool, mutation, poolMutation)
  val names: List[String]                    = all.map(_.name)
  def byName(name: String): Option[Strategy] = all.find(_.name == name)
}

sealed trait Tactic {
  def name: String
  def propose[A](context: Tactic.Context[A]): Option[Gen[Tactic.Candidate[A]]]
}

object Tactic {
  final case class Candidate[A](source: String, input: A, availableSources: List[String] = Nil)

  final case class Context[A](
      generatable: Generatable[A],
      feedback: Feedback[A],
      targetIds: Set[Int],
      pool: Option[ConstantPool]
  ) {
    def hasUncoveredTargets: Boolean = targetIds.exists(id => !feedback.covered(id))
  }

  case object Pool extends Tactic {
    val name: String = "pool"

    def propose[A](context: Context[A]): Option[Gen[Candidate[A]]] =
      context.pool
        .filter(p => !p.isEmpty && context.hasUncoveredTargets)
        .flatMap(context.generatable.pooled)
        .map(_.map(input => Candidate(name, input)))
  }

  case object Mutation extends Tactic {
    val name: String = "mutation"

    def propose[A](context: Context[A]): Option[Gen[Candidate[A]]] =
      context.feedback.corpus.lastOption.map(seed => context.generatable.mutate(seed).map(input => Candidate(name, input)))
  }
}
