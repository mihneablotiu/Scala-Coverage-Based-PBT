package benchmark

import benchmark.data.Tree

object MutationTargets {

  // Classifies a ledger by sortedness, negative entries, and duplicates.
  def sortedLedger(values: List[Int]): String =
    if (values.length < 4) "too-short"
    else {
      var sorted       = true
      var hasNegative  = false
      var hasDuplicate = false
      var rest         = values
      while (rest.nonEmpty) {
        if (rest.head < 0) hasNegative = true
        if (rest.tail.nonEmpty) {
          if (rest.head > rest.tail.head) sorted = false
          if (rest.head == rest.tail.head) hasDuplicate = true
        }
        rest = rest.tail
      }

      if (!sorted) "unsorted"
      else if (hasNegative) "negative"
      else if (hasDuplicate) "duplicate"
      else "clean"
    }

  // Classifies a list by duplicates and endpoint order.
  def uniqueEndpoints(values: List[Int]): String =
    if (values.length < 3) "too-short"
    else {
      var outer = values
      while (outer.nonEmpty) {
        var inner = outer.tail
        while (inner.nonEmpty) {
          if (outer.head == inner.head) return "duplicate"
          inner = inner.tail
        }
        outer = outer.tail
      }

      if (values.head < values.last) "ascending-edge"
      else if (values.head > values.last) "descending-edge"
      else "flat-edge"
    }

  // Classifies a triplet by component order and equality.
  def tripletOrder(a: Int, b: Int, c: Int): String =
    if (a > b) "first-drop"
    else if (b > c) "second-drop"
    else if (a == b && b == c) "flat"
    else if (a < b && b < c) "strictly-ascending"
    else "nondecreasing"

  // Classifies appointment times by order, duplicates, and gaps.
  def appointmentSchedule(minutes: List[Int]): String =
    if (minutes.length < 4) "too-few"
    else {
      var longGap = false
      var rest    = minutes
      while (rest.length >= 2) {
        val current = rest.head
        val next    = rest.tail.head
        if (current > next) return "out-of-order"
        if (current == next) return "double-booked"
        if (next - current > 60) longGap = true
        rest = rest.tail
      }

      if (minutes.head < 480) "too-early"
      else if (longGap) "gapped-day"
      else "packed-day"
    }

  // Compares two batches by sortedness and interval overlap.
  def mergeWindow(left: List[Int], right: List[Int]): String =
    if (left.length < 4 || right.length < 4) "small-batch"
    else {
      var leftRest = left
      while (leftRest.length >= 2) {
        if (leftRest.head > leftRest.tail.head) return "left-unsorted"
        leftRest = leftRest.tail
      }

      var rightRest = right
      while (rightRest.length >= 2) {
        if (rightRest.head > rightRest.tail.head) return "right-unsorted"
        rightRest = rightRest.tail
      }

      val overlaps = left.last >= right.head && right.last >= left.head

      if (overlaps) "joinable"
      else if (left.last < right.head) "left-before-right"
      else "right-before-left"
    }

  // Searches a list after first checking whether it is sorted.
  def binarySearchRoute(xs: List[Int], target: Int): String =
    if (xs.length < 5) "linear-small"
    else {
      var rest = xs
      while (rest.length >= 2) {
        if (rest.head > rest.tail.head) return "linear-scan"
        rest = rest.tail
      }

      var searchRest = xs
      while (searchRest.nonEmpty) {
        if (searchRest.head == target) return "found"
        searchRest = searchRest.tail
      }

      if (target < xs.head) "before"
      else if (target > xs.last) "after"
      else "gap"
    }

  // Classifies a tree by size and maximum depth.
  def treeDepth(t: Tree[Int]): String = {
    var size     = 0
    var maxDepth = 0
    var stack    = List((t, 0))

    while (stack.nonEmpty) {
      val (current, depth) = stack.head
      stack = stack.tail
      current match {
        case Tree.Leaf          => ()
        case Tree.Node(l, _, r) =>
          size += 1
          maxDepth = math.max(maxDepth, depth + 1)
          if (maxDepth >= 5) return "deep"
          stack = (l, depth + 1) :: (r, depth + 1) :: stack
      }
    }

    if (size == 0) "empty"
    else if (maxDepth <= 2) "shallow"
    else if (size >= 5) "wide"
    else "single-node"
  }
}
