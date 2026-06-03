package benchmark

import benchmark.data.Tree

/** Deep arms that need a whole *structured* input — an ordered list, a tall tree, or several of them at once. No single literal or numeric target
  * unlocks them (so the pool can't), a list index / tree node isn't a parameter the gradient can climb (so the gradient can't), and ScalaCheck's size
  * ramp doesn't help random: a longer random list is no more likely to be *sorted*, and tree generation halves its size budget each level so it stays
  * shallow.
  *
  * Only mutation reaches them, via the FuzzChick springboard — each rung is its own branch, so an input that climbed one rung higher is retained and
  * mutated one step further. The multi-parameter ones (`twoRuns`, `listThenTree`, `threeRuns`) are the point of the tuple mutators: those mutate *one
  * coordinate at a time*, so the search can freeze the hard-won part and perturb the rest. This is mutation's exclusive niche.
  */
object Sequences {

  // length of the longest strictly-increasing prefix, in elements.
  private def risingPrefixLen(xs: List[Int]): Int =
    xs.zip(xs.drop(1)).takeWhile { case (a, b) => a < b }.length + (if (xs.isEmpty) 0 else 1)

  // height of a binary tree.
  private def depth(t: Tree): Int = t match {
    case Tree.Leaf          => 0
    case Tree.Node(l, _, r) => 1 + math.max(depth(l), depth(r))
  }

  // one list: extend the strictly-increasing prefix one element at a time.
  def risingRun(xs: List[Int]): String = {
    val p = risingPrefixLen(xs)
    if (p < 2) "stall-1"
    else if (p < 3) "stall-2"
    else if (p < 4) "stall-3"
    else if (p < 5) "stall-4"
    else if (p < 6) "stall-5"
    else if (p < 7) "stall-6"
    else if (p < 8) "stall-7"
    else if (p < 9) "stall-8"
    else "rising"
  }

  // one tree: grow it taller, one Leaf→Node at a time. A rung per depth level, so each deeper tree is its own reward.
  def deepTree(t: Tree): String = {
    val d = depth(t)
    if (d < 2) "d1"
    else if (d < 3) "d2"
    else if (d < 4) "d3"
    else if (d < 5) "d4"
    else if (d < 6) "d5"
    else "d6"
  }

  // two lists (tuple2 mutator): climb a's run, then — holding a — climb b's. The mutator perturbs one coordinate at a time; a mutation that breaks the
  // sorted list covers nothing new so it is never retained, so the search keeps springing off the good (a-sorted) seed until it extends b.
  def twoRuns(a: List[Int], b: List[Int]): String = {
    val pa = risingPrefixLen(a)
    val pb = risingPrefixLen(b)
    if (pa < 2) "a1"
    else if (pa < 3) "a2"
    else if (pa < 4) "a3"
    else if (pa < 5) "a4"
    else if (pb < 2) "b1"
    else if (pb < 3) "b2"
    else if (pb < 4) "b3"
    else if (pb < 5) "b4"
    else "both-rising"
  }

  // a list and a tree (tuple2 mutator, mixed types): sort the list, then — holding it — grow the tree. Two different structural mutators in tandem.
  def listThenTree(xs: List[Int], t: Tree): String = {
    val p = risingPrefixLen(xs)
    val d = depth(t)
    if (p < 2) "list-1"
    else if (p < 3) "list-2"
    else if (p < 4) "list-3"
    else if (p < 5) "list-4"
    else if (d < 2) "tree-1"
    else if (d < 4) "tree-2"
    else "sorted-and-deep"
  }

  // three lists (tuple3 mutator): climb each run to length 4 in turn, holding the ones already done.
  def threeRuns(a: List[Int], b: List[Int], c: List[Int]): String = {
    val pa = risingPrefixLen(a)
    val pb = risingPrefixLen(b)
    val pc = risingPrefixLen(c)
    if (pa < 2) "a1"
    else if (pa < 3) "a2"
    else if (pa < 4) "a3"
    else if (pb < 2) "b1"
    else if (pb < 3) "b2"
    else if (pb < 4) "b3"
    else if (pc < 2) "c1"
    else if (pc < 3) "c2"
    else if (pc < 4) "c3"
    else "all-rising"
  }
}
