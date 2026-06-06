package pbt.gen

final case class ConstantPool(
    ints: Set[Int] = Set.empty,
    doubles: Set[Double] = Set.empty,
    strings: Set[String] = Set.empty,
    booleans: Set[Boolean] = Set.empty
) {
  def isEmpty: Boolean = ints.isEmpty && doubles.isEmpty && strings.isEmpty && booleans.isEmpty

  def ++(that: ConstantPool): ConstantPool =
    ConstantPool(ints ++ that.ints, doubles ++ that.doubles, strings ++ that.strings, booleans ++ that.booleans)

  def remove(used: ConstantPool): ConstantPool =
    ConstantPool(ints -- used.ints, doubles -- used.doubles, strings -- used.strings, booleans -- used.booleans)
}

object ConstantPool {
  val empty: ConstantPool = ConstantPool()
}
