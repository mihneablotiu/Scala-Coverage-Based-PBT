package pbt.gen

import org.scalacheck.Gen

/** Literals harvested from a method's guards, by type. The pool tactic injects these to hit equality/threshold branches; only the types a
  * [[Generatable]] knows how to inject are kept.
  */
final case class ConstantPool(
    ints: Set[Int] = Set.empty,
    longs: Set[Long] = Set.empty,
    doubles: Set[Double] = Set.empty,
    strings: Set[String] = Set.empty
) {
  def isEmpty: Boolean = ints.isEmpty && longs.isEmpty && doubles.isEmpty && strings.isEmpty

  def ++(o: ConstantPool): ConstantPool =
    ConstantPool(ints ++ o.ints, longs ++ o.longs, doubles ++ o.doubles, strings ++ o.strings)
}

object ConstantPool {
  val empty: ConstantPool = ConstantPool()

  /** Percent of draws (per injection point) that return a pooled literal instead of the base generator. */
  private val Percent = 30

  /** Draw a pooled value `Percent`% of the time, else defer to `base`. An empty pool is just `base`. */
  def inject[A](values: Set[A], base: Gen[A]): Gen[A] =
    if (values.isEmpty) base
    else Gen.frequency(Percent -> Gen.oneOf(values), (100 - Percent) -> base)
}
