package benchmark

import benchmark.data.Tree

object MutationTargets {

  // Models a ledger where order must be repaired before the later flags matter.
  // Expected: mutation/pool-mutation should help because `rest.head > rest.tail.head` blocks later outcomes until `seed.sorted` repairs the list.
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

  // Models a list whose endpoint checks are reachable only after the list is ordered.
  // Expected: mutation/pool-mutation should help because `rest.head > rest.tail.head` returns early, while the list mutator tries `seed.sorted`.
  def orderedEndpoints(values: List[Int]): String =
    if (values.length < 4) "too-short"
    else {
      var rest = values
      while (rest.length >= 2) {
        if (rest.head > rest.tail.head) return "unsorted"
        rest = rest.tail
      }

      if (values.head == values.last) "flat"
      else if (values.head < 0 && values.last > 0) "crosses-zero"
      else if (values.head >= 0) "nonnegative"
      else "negative"
    }

  // Models a staged triplet relation with nested component checks.
  // Expected: no tactic is expected to dominate in the current metric because the branch checks are cheap to evaluate; this is not a primary thesis example.
  def tripletOrder(a: Int, b: Int, c: Int): String = {
    var label = "not-dropping"
    if (a > b) {
      label = "needs-middle-zero"
      if (b == -b) {
        label = "low-tail"
        if (c == -c) label = "centered-drop"
        else if (c > a) label = "high-tail"
      }
    } else {
      if (a == -a) {
        if (b < c) label = "zero-led-rise"
        else label = "zero-led-flat"
      }
    }
    label
  }

  // Models appointment validation with early exits on invalid order.
  // Expected: mutation/pool-mutation should help because sorting a coverage-growing list can pass `current > next` and expose gap/time checks.
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

  // Models merging two sorted batches.
  // Expected: mutation/pool-mutation should help because tuple mutation can sort one list while preserving the other until both pass the sortedness guards.
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

  // Models a route that switches from linear scan to ordered search only after sorting.
  // Expected: mutation/pool-mutation should help on `linear-scan` because `seed.sorted` exposes `before`, `after`, and `gap`; `found` remains hard.
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

  // Models a tree whose deeper outcome requires growing structure.
  // Expected: mutation/pool-mutation should help because the tree mutator can replace `Leaf` with generated trees and attach generated subtrees.
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
