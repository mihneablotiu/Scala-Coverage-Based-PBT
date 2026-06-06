package pbt.gen

final case class ConstantPool(
    ints: Set[Int] = Set.empty,
    doubles: Set[Double] = Set.empty,
    strings: Set[String] = Set.empty
) {
  def isEmpty: Boolean = ints.isEmpty && doubles.isEmpty && strings.isEmpty

  def ++(that: ConstantPool): ConstantPool =
    ConstantPool(ints ++ that.ints, doubles ++ that.doubles, strings ++ that.strings)

  def remove(used: ConstantPool): ConstantPool =
    ConstantPool(ints -- used.ints, doubles -- used.doubles, strings -- used.strings)
}

object ConstantPool {
  val empty: ConstantPool = ConstantPool()
}
