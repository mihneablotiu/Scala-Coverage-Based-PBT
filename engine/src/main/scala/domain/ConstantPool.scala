package domain

import org.scalacheck.{Arbitrary, Gen}

/** Per-method literal dictionary, keyed by Scala primitive kind. Empty for non-pool strategies. */
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

  /** AFL/Dragen² convention. */
  val PoolProb: Double = 0.30

  /** Wrap `base` so each draw returns a pool value with probability `prob`, else defers to `base`. Empty pool ⇒ identity wrap (no overhead). */
  def withPool[A](pool: Set[A], prob: Double, base: Arbitrary[A]): Arbitrary[A] =
    if (pool.isEmpty) base
    else {
      val poolWeight = math.round(prob * 100).toInt
      val baseWeight = 100 - poolWeight
      Arbitrary(Gen.frequency(poolWeight -> Gen.oneOf(pool.toSeq), baseWeight -> base.arbitrary))
    }
}
