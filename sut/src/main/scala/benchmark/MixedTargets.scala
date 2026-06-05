package benchmark

import benchmark.data.Tree

object MixedTargets {

  def eventStream(events: List[Int], priority: Int): String =
    if (priority == 9001) "manual-review"
    else {
      var ordered     = true
      var reversed    = true
      var serverError = false
      var rest        = events
      while (rest.nonEmpty) {
        if (rest.head == 500) serverError = true
        if (rest.tail.nonEmpty) {
          if (rest.head > rest.tail.head) ordered = false
          if (rest.head < rest.tail.head) reversed = false
        }
        rest = rest.tail
      }

      if (serverError) "server-error"
      else if (ordered) "ordered-stream"
      else if (reversed) "reverse-stream"
      else "unordered"
    }

  def reconciliation(left: List[Int], right: List[Int], code: Int): String =
    if (code == 271828) "literal"
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

      var matched = false
      var outer   = left
      while (outer.nonEmpty && !matched) {
        var inner = right
        while (inner.nonEmpty && !matched) {
          if (outer.head == inner.head) matched = true
          inner = inner.tail
        }
        outer = outer.tail
      }

      if (!leftSorted || !rightSorted) "unsorted"
      else if (matched) "matched"
      else if (left.nonEmpty && right.nonEmpty && left.last < right.head) "left-before"
      else "right-before"
    }

  def cacheProbe(keys: List[Int], hotKey: Int): String = {
    var knownHot = false
    var sorted   = true
    var hit      = false
    var rest     = keys
    while (rest.nonEmpty) {
      if (rest.head == 424242) knownHot = true
      if (rest.head == hotKey) hit = true
      if (rest.tail.nonEmpty && rest.head > rest.tail.head) sorted = false
      rest = rest.tail
    }

    if (knownHot) "known-hot"
    else if (!sorted) "scan"
    else if (hit) "indexed-hit"
    else if (keys.isEmpty) "empty"
    else "indexed-miss"
  }

  def treeLookup(t: Tree[Int], key: Int): String = {
    var found    = false
    var maxDepth = 0
    var stack    = List((t, 0))
    while (stack.nonEmpty && !found) {
      val (current, depth) = stack.head
      stack = stack.tail
      current match {
        case Tree.Leaf          => maxDepth = maxDepth
        case Tree.Node(l, v, r) =>
          found = v == key
          maxDepth = math.max(maxDepth, depth + 1)
          stack = (l, depth + 1) :: (r, depth + 1) :: stack
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

    if (key == 65535) "sentinel"
    else if (isSearchTree && found) "tree-hit"
    else if (isSearchTree) "tree-miss"
    else if (maxDepth >= 5) "deep-fallback"
    else "fallback"
  }

  def batchWindow(values: List[Int], low: Int, high: Int): String =
    if (low == -1000000 || high == 1000000) "wide-open"
    else {
      var sorted  = true
      var overlap = false
      var rest    = values
      while (rest.nonEmpty) {
        if (rest.head >= low && rest.head <= high) overlap = true
        if (rest.tail.nonEmpty && rest.head > rest.tail.head) sorted = false
        rest = rest.tail
      }

      if (!sorted) "unsorted"
      else if (overlap) "overlap"
      else if (values.nonEmpty && values.last < low) "below"
      else "above"
    }
}
