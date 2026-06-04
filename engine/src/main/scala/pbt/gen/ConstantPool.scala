package pbt.gen

import org.scalacheck.Gen

/** The literals mined from a method's body — just the types we can inject (`Int`, `String`). The Pool tactic splices these into draws to hit equality
  * / threshold branches a random value would almost never satisfy.
  */
final case class ConstantPool(ints: Set[Int], strings: Set[String]) {
  def isEmpty: Boolean = ints.isEmpty && strings.isEmpty
}

object ConstantPool {
  val empty: ConstantPool = ConstantPool(Set.empty, Set.empty)

  /** Draw a pooled literal 30% of the time, otherwise defer to `base`. An empty set is just `base`. */
  def inject[A](literals: Set[A], base: Gen[A]): Gen[A] =
    if (literals.isEmpty) base
    else Gen.frequency(30 -> Gen.oneOf(literals), 70 -> base)
}
