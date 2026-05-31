package domain

import org.scalacheck.{Arbitrary, Gen}

/** Builds a pool-aware `Arbitrary[A]` from a [[ConstantPool]]. Foundational instances splice mined literals into primitives; compositional instances
  * recurse through tuples / lists. The low-priority default falls back to the user's `Arbitrary[A]` for types the engine doesn't know structurally,
  * so SUT-agnostic types still compile (without pool effects).
  *
  * Independent of [[Mutator]] by design — the four [[Strategy]] cases compose `Pooled` and `Mutator` separately.
  */
trait Pooled[A] {
  def arb(pool: ConstantPool): Arbitrary[A]
}

object Pooled extends LowPriorityPooled {

  def apply[A](implicit p: Pooled[A]): Pooled[A] = p

  implicit val intPooled: Pooled[Int] = pool => ConstantPool.withPool(pool.ints, ConstantPool.PoolProb, Arbitrary.arbInt)

  implicit val booleanPooled: Pooled[Boolean] = pool => ConstantPool.withPool(pool.booleans, ConstantPool.PoolProb, Arbitrary.arbBool)

  implicit val longPooled: Pooled[Long] = pool => ConstantPool.withPool(pool.longs, ConstantPool.PoolProb, Arbitrary.arbLong)

  implicit val charPooled: Pooled[Char] = pool => ConstantPool.withPool(pool.chars, ConstantPool.PoolProb, Arbitrary.arbChar)

  implicit val stringPooled: Pooled[String] = pool => ConstantPool.withPool(pool.strings, ConstantPool.PoolProb, Arbitrary.arbString)

  implicit def tuple2Pooled[A: Pooled, B: Pooled]: Pooled[(A, B)] = pool =>
    Arbitrary(for {
      a <- Pooled[A].arb(pool).arbitrary
      b <- Pooled[B].arb(pool).arbitrary
    } yield (a, b))

  implicit def tuple3Pooled[A: Pooled, B: Pooled, C: Pooled]: Pooled[(A, B, C)] = pool =>
    Arbitrary(for {
      a <- Pooled[A].arb(pool).arbitrary
      b <- Pooled[B].arb(pool).arbitrary
      c <- Pooled[C].arb(pool).arbitrary
    } yield (a, b, c))

  implicit def listPooled[A: Pooled]: Pooled[List[A]] = pool => Arbitrary(Gen.listOf(Pooled[A].arb(pool).arbitrary))
}

trait LowPriorityPooled {
  implicit def defaultPooled[A](implicit base: Arbitrary[A]): Pooled[A] = _ => base
}
