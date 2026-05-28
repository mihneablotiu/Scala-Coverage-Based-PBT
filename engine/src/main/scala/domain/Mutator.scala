package domain

import org.scalacheck.{Arbitrary, Gen}

/** How to produce "nearby" variants of a value of type `A`.
  *
  * Used by [[Strategy.MutationGuided]] to derive the next input from a previously interesting one
  * (a seed that covered a new branch). A strategy that doesn't mutate doesn't depend on this type
  * class — the requirement is per-strategy, not per-call-site.
  *
  * The instance shape follows the SUT type:
  *
  *   - **Primitives** (`Boolean`, `Int`) use AFL-style "interesting value" sets — flip the bit,
  *     bump by one, jump to a boundary. The four-schema decomposition from FuzzChick (mutate /
  *     drop-to-subterm / drop-to-smaller-constructor / switch-constructor) doesn't apply: there's
  *     no constructor to recurse into.
  *   - **ADTs** (`List[Int]`) realise FuzzChick's four schemas concretely: mutate the head
  *     (`schema_r`), recurse into the tail (`schema_r`), drop to the tail (`schema_s`), drop to
  *     `Nil` (`schema_d`), and grow by prepending (the constructor-switch surrogate when the only
  *     other constructor is `Nil`).
  *   - **Tuples** mutate exactly one component at a time, derived once and reused for any `(A, B)`
  *     / `(A, B, C)` whose components themselves have a `Mutator`.
  */
trait Mutator[A] {
  def mutate(a: A): Gen[A]
}

object Mutator {

  def apply[A](implicit m: Mutator[A]): Mutator[A] = m

  implicit val boolMutator: Mutator[Boolean] = b => Gen.const(!b)

  implicit val intMutator: Mutator[Int] = n =>
    Gen.oneOf(
      n + 1,
      n - 1,
      -n,
      0,
      1,
      -1,
      Int.MaxValue,
      Int.MinValue
    )

  /** `lazy` because the recursive case calls `listIntMutator` on the tail — without `lazy val` the
    * initialiser would dereference an uninitialised reference.
    */
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

  implicit def tuple3Mutator[A: Mutator, B: Mutator, C: Mutator]: Mutator[(A, B, C)] = {
    case (a, b, c) =>
      Gen.oneOf(
        Mutator[A].mutate(a).map((_, b, c)),
        Mutator[B].mutate(b).map((a, _, c)),
        Mutator[C].mutate(c).map((a, b, _))
      )
  }
}
