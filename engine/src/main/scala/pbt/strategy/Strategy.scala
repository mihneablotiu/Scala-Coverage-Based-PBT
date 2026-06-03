package pbt.strategy

import pbt.strategy.Tactic._

/** A named set of tactics. `random` is the empty set (stock ScalaCheck); every other strategy is a subset of `{Pool, Mutation, Gradient}`. The eight
  * presets below are exactly those eight subsets — the experiment sweeps them, simplest → most complex.
  */
final case class Strategy(name: String, tactics: Set[Tactic.Kind])

object Strategy {

  val all: List[Strategy] = List(
    Strategy("random", Set.empty),
    Strategy("random-pool", Set(Pool)),
    Strategy("mutation-guided", Set(Mutation)),
    Strategy("mutation-guided-pool", Set(Mutation, Pool)),
    Strategy("coverage-guided", Set(Gradient)),
    Strategy("coverage-guided-pool", Set(Gradient, Pool)),
    Strategy("coverage-guided-mutation-guided", Set(Gradient, Mutation)),
    Strategy("coverage-guided-mutation-guided-pool", Set(Gradient, Mutation, Pool))
  )

  val names: List[String]                    = all.map(_.name)
  def byName(name: String): Option[Strategy] = all.find(_.name == name)
}
