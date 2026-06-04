package pbt.gen

import org.scalacheck.Gen

/** How to produce inputs of type `A`, behind three methods:
  *   - `arbitrary` — a uniform draw, exactly ScalaCheck's (this is all `random` uses);
  *   - `mutate` — a small edit of a seed (the Mutation tactic perturbs corpus seeds);
  *   - `pooled` — a draw that may splice in a mined source literal (the Pool tactic).
  *
  * Every supported type has one instance, all gathered in `app.Generators`.
  */
trait Generatable[A] {
  def arbitrary: Gen[A]
  def mutate(seed: A): Gen[A]
  def pooled(pool: ConstantPool): Gen[A]
}

object Generatable {
  def apply[A](implicit g: Generatable[A]): Generatable[A] = g
}
