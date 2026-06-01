package domain

import org.scalacheck.Gen

/** Per-method literal dictionary, keyed by Scala primitive kind. Empty for non-pool strategies. Mined once at parse time; consumed by
  * [[Generatable]]'s `pooled`.
  */
final case class ConstantPool(
    ints: Set[Int],
    longs: Set[Long],
    floats: Set[Float],
    doubles: Set[Double],
    booleans: Set[Boolean],
    chars: Set[Char],
    strings: Set[String],
    bytes: Set[Byte],
    shorts: Set[Short]
)

object ConstantPool {

  val empty: ConstantPool = ConstantPool(
    Set.empty,
    Set.empty,
    Set.empty,
    Set.empty,
    Set.empty,
    Set.empty,
    Set.empty,
    Set.empty,
    Set.empty
  )

  /** AFL/Dragen² convention: a mined value is drawn 30% of the time, the base generator 70%. */
  val PoolProb: Double = 0.30

  /** Mix mined `values` into `base`: each draw returns a pool value with probability [[PoolProb]], else defers to `base`. Empty pool ⇒ `base`
    * unchanged (no overhead).
    */
  def inject[A](values: Set[A], base: Gen[A]): Gen[A] =
    if (values.isEmpty) base
    else {
      val poolWeight = math.round(PoolProb * 100).toInt
      Gen.frequency(poolWeight -> Gen.oneOf(values), (100 - poolWeight) -> base)
    }
}
