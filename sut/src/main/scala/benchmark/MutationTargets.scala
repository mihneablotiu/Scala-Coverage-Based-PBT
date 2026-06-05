package benchmark

import benchmark.data.Tree

object MutationTargets {

  def priceTrend(prices: List[Int]): String =
    if (prices.length < 2) "too-short"
    else {
      var nonDecreasing = true
      var nonIncreasing = true
      var changes       = 0
      var previousSign  = 0
      var rest          = prices
      while (rest.length >= 2) {
        val a    = rest.head
        val b    = rest.tail.head
        val sign = java.lang.Integer.signum(b - a)

        if (a > b) nonDecreasing = false
        if (a < b) nonIncreasing = false
        if (sign != 0) {
          if (previousSign != 0 && sign != previousSign) changes += 1
          previousSign = sign
        }
        rest = rest.tail
      }

      if (prices.length < 20) "insufficient-history"
      else if (nonDecreasing && nonIncreasing) "flat"
      else if (nonDecreasing) "rising"
      else if (nonIncreasing) "falling"
      else if (changes >= 3) "volatile"
      else "mixed"
    }

  def inventoryProfile(stock: List[Int]): String =
    if (stock.isEmpty) "empty"
    else {
      var nonDecreasing = true
      var hasDuplicates = false
      var rest          = stock
      while (rest.length >= 2) {
        val a = rest.head
        val b = rest.tail.head
        if (a > b) nonDecreasing = false
        if (a == b) hasDuplicates = true
        rest = rest.tail
      }

      if (stock.length < 25) "small-sample"
      else if (!nonDecreasing) "unsorted"
      else if (hasDuplicates) "grouped-duplicates"
      else if (stock.head < 0) "negative-ledger"
      else "grouped"
    }

  def mergeJoinShape(left: List[Int], right: List[Int]): String =
    if (left.length < 15 || right.length < 15) "small-batch"
    else {
      var leftSorted = true
      var leftRest   = left
      while (leftRest.length >= 2) {
        if (leftRest.head > leftRest.tail.head) leftSorted = false
        leftRest = leftRest.tail
      }

      var rightSorted = true
      var rightRest   = right
      while (rightRest.length >= 2) {
        if (rightRest.head > rightRest.tail.head) rightSorted = false
        rightRest = rightRest.tail
      }

      val overlaps = left.last >= right.head && right.last >= left.head

      if (!leftSorted) "left-unsorted"
      else if (!rightSorted) "right-unsorted"
      else if (overlaps) "joinable"
      else if (left.last < right.head) "left-before-right"
      else "right-before-left"
    }

  def binarySearchRoute(xs: List[Int], target: Int): String =
    if (xs.length < 32) "linear-small"
    else {
      var sorted = true
      var rest   = xs
      while (rest.length >= 2) {
        if (rest.head > rest.tail.head) sorted = false
        rest = rest.tail
      }

      var found      = false
      var searchRest = xs
      while (searchRest.nonEmpty && !found) {
        if (searchRest.head == target) found = true
        searchRest = searchRest.tail
      }

      if (!sorted) "linear-scan"
      else if (found) "found"
      else if (target < xs.head) "before"
      else if (target > xs.last) "after"
      else "gap"
    }

  def treeIndexProfile(t: Tree[Int]): String = {
    var size       = 0
    var maxDepth   = 0
    var stack      = List((t, 0))
    var leftDepth  = 0
    var rightDepth = 0

    while (stack.nonEmpty) {
      val (current, depth) = stack.head
      stack = stack.tail
      current match {
        case Tree.Leaf          => maxDepth = maxDepth
        case Tree.Node(l, _, r) =>
          size += 1
          maxDepth = math.max(maxDepth, depth + 1)
          stack = (l, depth + 1) :: (r, depth + 1) :: stack
      }
    }

    t match {
      case Tree.Leaf          => leftDepth = leftDepth
      case Tree.Node(l, _, r) =>
        var leftStack = List((l, 0))
        while (leftStack.nonEmpty) {
          val (current, depth) = leftStack.head
          leftStack = leftStack.tail
          current match {
            case Tree.Leaf            => leftDepth = leftDepth
            case Tree.Node(ll, _, rr) =>
              leftDepth = math.max(leftDepth, depth + 1)
              leftStack = (ll, depth + 1) :: (rr, depth + 1) :: leftStack
          }
        }

        var rightStack = List((r, 0))
        while (rightStack.nonEmpty) {
          val (current, depth) = rightStack.head
          rightStack = rightStack.tail
          current match {
            case Tree.Leaf            => rightDepth = rightDepth
            case Tree.Node(ll, _, rr) =>
              rightDepth = math.max(rightDepth, depth + 1)
              rightStack = (ll, depth + 1) :: (rr, depth + 1) :: rightStack
          }
        }
    }

    var isSearchTree = true
    var bounds       = List((t, Option.empty[Int], Option.empty[Int]))
    while (bounds.nonEmpty && isSearchTree) {
      val (current, min, max) = bounds.head
      bounds = bounds.tail
      current match {
        case Tree.Leaf          => isSearchTree = isSearchTree
        case Tree.Node(l, v, r) =>
          val minOk = min match {
            case Some(m) => m <= v
            case None    => true
          }
          val maxOk = max match {
            case Some(m) => v <= m
            case None    => true
          }
          isSearchTree = minOk && maxOk
          bounds = (l, min, Some(v)) :: (r, Some(v), max) :: bounds
      }
    }

    if (size == 0) "empty"
    else if (size < 15) "small-tree"
    else if (isSearchTree && math.abs(leftDepth - rightDepth) <= 1) "balanced-index"
    else if (isSearchTree) "index"
    else if (maxDepth >= 5) "deep-heap"
    else if (leftDepth >= rightDepth + 2) "left-heavy"
    else if (rightDepth >= leftDepth + 2) "right-heavy"
    else "ordinary"
  }
}
