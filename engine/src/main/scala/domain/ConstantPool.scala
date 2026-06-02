package domain

import org.scalacheck.Gen

/** Literals mined from a method body, by kind. Only the kinds a [[Generatable]] actually injects are kept (the SUT's guards use `Int`, `Long`,
  * `Double`, `String`); a new kind is added here and consumed in the matching `Generatable.pooled`.
  */
final case class ConstantPool(
    ints: Set[Int],
    longs: Set[Long],
    doubles: Set[Double],
    strings: Set[String]
)

object ConstantPool {

  val empty: ConstantPool = ConstantPool(Set.empty, Set.empty, Set.empty, Set.empty)

  /** Percent of draws that return a mined literal instead of the base generator (AFL/Dragen²). */
  val PoolPercent: Int = 30

  /** Draw a mined value with probability [[PoolPercent]]%, else defer to `base`. Empty pool ⇒ `base`. */
  def inject[A](values: Set[A], base: Gen[A]): Gen[A] =
    if (values.isEmpty) base
    else Gen.frequency(PoolPercent -> Gen.oneOf(values), (100 - PoolPercent) -> base)
}
