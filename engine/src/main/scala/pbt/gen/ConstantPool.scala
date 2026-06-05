package pbt.gen

final case class ConstantPool(ints: Set[Int]) {
  def isEmpty: Boolean = ints.isEmpty
}

object ConstantPool {
  val empty: ConstantPool = ConstantPool(Set.empty)
}
