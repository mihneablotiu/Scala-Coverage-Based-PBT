package pbt.gen

final case class ConstantPool(
    ints: Set[Int] = Set.empty,
    doubles: Set[Double] = Set.empty,
    strings: Set[String] = Set.empty,
    booleans: Set[Boolean] = Set.empty
) {
  def isEmpty: Boolean = ints.isEmpty && doubles.isEmpty && strings.isEmpty && booleans.isEmpty
}

object ConstantPool {
  val empty: ConstantPool = ConstantPool()
}
