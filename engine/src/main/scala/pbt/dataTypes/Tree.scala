package pbt.dataTypes

/** A small binary tree — a structurally-rich, recursive input type for benchmarks. Generic in its element so it composes like `List`/`Option`. */
sealed trait Tree[+A]
object Tree {
  case object Leaf                                                  extends Tree[Nothing]
  final case class Node[A](left: Tree[A], value: A, right: Tree[A]) extends Tree[A]
}
