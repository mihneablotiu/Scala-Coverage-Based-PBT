package domain

import org.scalacheck.{Arbitrary, Gen}

/** Everything a [[Strategy]] needs to produce inputs of type `A`, in one type class:
  *
  *   - `arbitrary` — a fresh uniform draw (exactly ScalaCheck's `Arbitrary[A]`);
  *   - `mutate` — a "nearby" variant of a seed (AFL-style interesting values for primitives; FuzzChick's four schemas for lists; one component at a
  *     time for tuples);
  *   - `pooled` — a fresh draw that splices in mined source literals ([[ConstantPool]]).
  *
  * One bound `[A: Generatable]` replaces the old `Arbitrary` + `Mutator` + `Pooled` trio across the engine.
  */
trait Generatable[A] {
  def arbitrary: Gen[A]
  def mutate(seed: A): Gen[A]
  def pooled(pool: ConstantPool): Gen[A]
}

object Generatable {

  def apply[A](implicit g: Generatable[A]): Generatable[A] = g

  def instance[A](arb: Gen[A])(mut: A => Gen[A])(pool: ConstantPool => Gen[A]): Generatable[A] =
    new Generatable[A] {
      def arbitrary: Gen[A]                   = arb
      def mutate(seed: A): Gen[A]             = mut(seed)
      def pooled(pool0: ConstantPool): Gen[A] = pool(pool0)
    }

  /** Fallback for a type that has only an `Arbitrary`: `mutate` and `pooled` both degrade to a fresh draw. Deliberately **not** implicit — a missing
    * structural instance should be a compile error, not a silent degradation of a guided strategy to plain random (which would contaminate the
    * comparison). Wire it explicitly in the composition root when adding such a type.
    */
  def fromArbitrary[A](implicit a: Arbitrary[A]): Generatable[A] =
    instance(a.arbitrary)(_ => a.arbitrary)(_ => a.arbitrary)

  implicit val boolean: Generatable[Boolean] =
    instance(Arbitrary.arbBool.arbitrary)(b => Gen.const(!b))(p => ConstantPool.inject(p.booleans, Arbitrary.arbBool.arbitrary))

  implicit val int: Generatable[Int] =
    instance(Arbitrary.arbInt.arbitrary)(n => Gen.oneOf(n + 1, n - 1, -n, 0, 1, -1, Int.MaxValue, Int.MinValue))(p =>
      ConstantPool.inject(p.ints, Arbitrary.arbInt.arbitrary)
    )

  /** `lazy` because the recursive arm references `listInt` itself. */
  implicit val listInt: Generatable[List[Int]] = {
    lazy val self: Generatable[List[Int]] = instance[List[Int]](Gen.listOf(int.arbitrary)) {
      case Nil =>
        int.arbitrary.map(_ :: Nil)
      case xs @ h :: t =>
        Gen.oneOf(
          int.mutate(h).map(_ :: t),
          self.mutate(t).map(h :: _),
          Gen.const(t),
          Gen.const(Nil),
          int.arbitrary.map(_ :: xs)
        )
    }(p => Gen.listOf(int.pooled(p)))
    self
  }

  implicit def tuple2[A, B](implicit fa: Generatable[A], fb: Generatable[B]): Generatable[(A, B)] =
    instance[(A, B)](Gen.zip(fa.arbitrary, fb.arbitrary)) { case (a, b) =>
      Gen.oneOf(fa.mutate(a).map((_, b)), fb.mutate(b).map((a, _)))
    }(p => Gen.zip(fa.pooled(p), fb.pooled(p)))

  implicit def tuple3[A, B, C](implicit fa: Generatable[A], fb: Generatable[B], fc: Generatable[C]): Generatable[(A, B, C)] =
    instance[(A, B, C)](Gen.zip(fa.arbitrary, fb.arbitrary, fc.arbitrary)) { case (a, b, c) =>
      Gen.oneOf(fa.mutate(a).map((_, b, c)), fb.mutate(b).map((a, _, c)), fc.mutate(c).map((a, b, _)))
    }(p => Gen.zip(fa.pooled(p), fb.pooled(p), fc.pooled(p)))
}
