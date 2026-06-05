package pbt.strategy

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

sealed trait Tactic

object Tactic {
  case object Pool     extends Tactic
  case object Mutation extends Tactic
}
