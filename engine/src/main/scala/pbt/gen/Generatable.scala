package pbt.gen

import org.scalacheck.Gen
import pbt.targeting.BranchGoal

trait Generatable[A] {
  def arbitrary: Gen[A]
  def mutate(seed: A): Gen[A]
  def pooled(pool: ConstantPool): Option[Gen[A]]
  def targeted(seed: A, goal: BranchGoal): Option[Gen[A]] = None
}

object Generatable {
  def apply[A](implicit g: Generatable[A]): Generatable[A] = g
}
