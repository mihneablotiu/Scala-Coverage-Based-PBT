package benchmark

import benchmark.data.Tree

/** Recursive / structural invariants (sorted, ordered runs, BST). P(a random input is valid) decays with size, so the "valid" arms are starved;
  * mutation can climb toward validity one edit at a time. Exercises a `fold` with a branchy lambda and nested helper `def`s (which stay opaque in the
  * graph).
  */
object StructuralInvariants {

  // size >= 8: a random list this long is essentially never sorted, so the ordered arms are starved.
  def isStrictlySorted(xs: List[Int]): String =
    if (xs.size < 8) "too-short"
    else if (xs.lazyZip(xs.tail).forall(_ < _)) "strictly-sorted"
    else if (xs.lazyZip(xs.tail).forall(_ <= _)) "non-decreasing"
    else "unsorted"

  def runLengthShape(xs: List[Int]): String = {
    val longestRun = xs
      .lazyZip(xs.tail)
      .foldLeft((1, 1)) { case ((run, best), (a, b)) =>
        if (a <= b) (run + 1, math.max(best, run + 1)) else (1, best)
      }
      ._2
    if (xs.isEmpty) "empty"
    else if (longestRun == xs.size) "fully-non-decreasing"
    else if (longestRun >= xs.size / 2) "mostly-ordered"
    else "scattered"
  }

  def bstShape(t: Tree): String = {
    def isBst(node: Tree, lo: Int, hi: Int): Boolean = node match {
      case Tree.Leaf          => true
      case Tree.Node(l, v, r) => v > lo && v < hi && isBst(l, lo, v) && isBst(r, v, hi)
    }
    def size(node: Tree): Int = node match {
      case Tree.Leaf          => 0
      case Tree.Node(l, _, r) => 1 + size(l) + size(r)
    }
    t match {
      case Tree.Leaf => "empty"
      case _         =>
        // A valid BST of >= 8 nodes is something random trees essentially never form.
        if (!isBst(t, Int.MinValue, Int.MaxValue)) "not-bst"
        else if (size(t) >= 8) "large-bst"
        else "small-bst"
    }
  }
}
