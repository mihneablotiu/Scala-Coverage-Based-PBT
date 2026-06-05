package benchmark.data

sealed trait Tree[+A]

object Tree {
  case object Leaf                                                  extends Tree[Nothing]
  final case class Node[A](left: Tree[A], value: A, right: Tree[A]) extends Tree[A]
}
