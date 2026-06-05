package pbt.gen

import org.scalacheck.Gen

final case class ConstantPool(ints: Set[Int]) {
  def isEmpty: Boolean = ints.isEmpty
}

object ConstantPool {
  val empty: ConstantPool = ConstantPool(Set.empty)

  def inject[A](literals: Set[A], base: Gen[A]): Gen[A] =
    if (literals.isEmpty) base
    else Gen.frequency(30 -> Gen.oneOf(literals), 70 -> base)
}
