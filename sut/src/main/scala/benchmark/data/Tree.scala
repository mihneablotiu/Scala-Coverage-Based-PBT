package benchmark.data

/** A small binary tree — a structurally-rich, recursive input type for the benchmarks. */
sealed trait Tree
object Tree {
  case object Leaf                                           extends Tree
  final case class Node(left: Tree, value: Int, right: Tree) extends Tree
}
