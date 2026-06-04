package pbt.strategy

/** Which feedback channels a run uses — two independent on/off switches, nothing more. `random` uses neither (stock ScalaCheck); `pool` injects mined
  * literals; `mutation` perturbs corpus seeds; `pool-mutation` does both. Every switched-on channel is coverage-guided; that's the whole vocabulary.
  */
final case class Strategy(name: String, pool: Boolean, mutation: Boolean)

object Strategy {
  val random: Strategy       = Strategy("random", pool = false, mutation = false)
  val pool: Strategy         = Strategy("pool", pool = true, mutation = false)
  val mutation: Strategy     = Strategy("mutation", pool = false, mutation = true)
  val poolMutation: Strategy = Strategy("pool-mutation", pool = true, mutation = true)

  val all: List[Strategy]                    = List(random, pool, mutation, poolMutation)
  val names: List[String]                    = all.map(_.name)
  def byName(name: String): Option[Strategy] = all.find(_.name == name)
}
