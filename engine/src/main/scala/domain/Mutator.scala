package domain

import org.scalacheck.{Arbitrary, Gen}

/** Produces "nearby" variants of a value. Used by [[Strategy.MutationGuided]]; strategies that don't mutate don't depend on it.
  *
  * Primitives use AFL-style interesting-value sets (boundary jumps, ±1). ADTs realise FuzzChick's four schemas (mutate head, recurse into tail, drop,
  * switch constructor). Tuples mutate exactly one component at a time.
  */
trait Mutator[A] {
  def mutate(a: A): Gen[A]
}

object Mutator {

  def apply[A](implicit m: Mutator[A]): Mutator[A] = m

  implicit val boolMutator: Mutator[Boolean] = b => Gen.const(!b)

  implicit val intMutator: Mutator[Int] = n => Gen.oneOf(n + 1, n - 1, -n, 0, 1, -1, Int.MaxValue, Int.MinValue)

  /** `lazy` because the recursive arm calls `listIntMutator` on the tail. */
  implicit lazy val listIntMutator: Mutator[List[Int]] = {
    case Nil =>
      Arbitrary.arbitrary[Int].map(_ :: Nil)
    case xs @ h :: t =>
      Gen.oneOf(
        intMutator.mutate(h).map(_ :: t),
        listIntMutator.mutate(t).map(h :: _),
        Gen.const(t),
        Gen.const(Nil),
        Arbitrary.arbitrary[Int].map(_ :: xs)
      )
  }

  implicit def tuple2Mutator[A: Mutator, B: Mutator]: Mutator[(A, B)] = { case (a, b) =>
    Gen.oneOf(
      Mutator[A].mutate(a).map((_, b)),
      Mutator[B].mutate(b).map((a, _))
    )
  }

  implicit def tuple3Mutator[A: Mutator, B: Mutator, C: Mutator]: Mutator[(A, B, C)] = { case (a, b, c) =>
    Gen.oneOf(
      Mutator[A].mutate(a).map((_, b, c)),
      Mutator[B].mutate(b).map((a, _, c)),
      Mutator[C].mutate(c).map((a, b, _))
    )
  }
}
