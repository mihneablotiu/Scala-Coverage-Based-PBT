package pbt.gen

import org.scalacheck.Gen

trait Generatable[A] {
  def arbitrary: Gen[A]
  def mutate(seed: A): Gen[A]
  def pooled(pool: ConstantPool): Option[Gen[A]]
}

object Generatable {
  def apply[A](implicit g: Generatable[A]): Generatable[A] = g
}
