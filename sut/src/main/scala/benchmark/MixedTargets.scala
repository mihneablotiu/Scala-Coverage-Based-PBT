package benchmark

object MixedTargets {

  // Routes event streams by priority, event order, and error markers.
  def eventStream(events: List[Int], priority: Int): String =
    if (priority == 9001) "manual-review"
    else if (events.length < 4) "too-short"
    else {
      var ordered  = true
      var reversed = true
      var rest     = events
      while (rest.nonEmpty) {
        if (rest.head == 500) return "server-error"
        if (rest.tail.nonEmpty) {
          if (rest.head > rest.tail.head) ordered = false
          if (rest.head < rest.tail.head) reversed = false
        }
        rest = rest.tail
      }

      val score = priority.toLong * 3L + events.length.toLong
      if (score < 1000000L) "low-priority"
      else if (score > 1001000L) "high-priority"
      else if (ordered) "ordered-stream"
      else if (reversed) "reverse-stream"
      else "unordered"
    }

  // Reconciles two batches by code, order, shared values, and distance.
  def reconciliation(left: List[Int], right: List[Int], code: Int): String =
    if (code == 271828) "literal"
    else if (left.length < 3 || right.length < 3) "small-batch"
    else {
      var leftRest = left
      while (leftRest.length >= 2) {
        if (leftRest.head > leftRest.tail.head) return "unsorted"
        leftRest = leftRest.tail
      }

      var rightRest = right
      while (rightRest.length >= 2) {
        if (rightRest.head > rightRest.tail.head) return "unsorted"
        rightRest = rightRest.tail
      }

      var outer = left
      while (outer.nonEmpty) {
        var inner = right
        while (inner.nonEmpty) {
          if (outer.head == inner.head) return "matched"
          inner = inner.tail
        }
        outer = outer.tail
      }

      val distance = right.head.toLong - left.last.toLong + code.toLong
      if (distance < 1000000L) "close"
      else if (distance > 1001000L) "far"
      else if (left.nonEmpty && right.nonEmpty && left.last < right.head) "left-before"
      else "right-before"
    }

  // Probes cache keys by hot markers, key order, and probe range.
  def cacheProbe(keys: List[Int], hotKey: Int): String = {
    var knownHot = false
    var sorted   = true
    var rest     = keys
    while (rest.nonEmpty) {
      if (rest.head == 424242) knownHot = true
      if (rest.head == hotKey) return "indexed-hit"
      if (rest.tail.nonEmpty && rest.head > rest.tail.head) sorted = false
      rest = rest.tail
    }

    val score = hotKey.toLong * 2L + keys.length.toLong
    if (knownHot) "known-hot"
    else if (score < 1000000L) "cold-range"
    else if (score > 1000100L) "hot-range"
    else if (!sorted) "scan"
    else if (keys.isEmpty) "empty"
    else "indexed-miss"
  }

  // Approves a batch through a literal code, ordered values, and a score band.
  def simpleApproval(values: List[Int], code: Int): String =
    if (code == 2024) {
      if (values.length >= 3) {
        val first  = values.head
        val second = values.tail.head
        val third  = values.tail.tail.head

        if (first <= second) {
          if (second <= third) {
            val score = code.toLong + values.last.toLong
            if (score >= 2500L) {
              if (score <= 2600L) {
                if (first == 7) "lucky-approved"
                else "approved"
              } else "score-high"
            } else "score-low"
          } else "second-drop"
        } else "first-drop"
      } else "too-short"
    } else "wrong-code"

  // Classifies a ticket batch by code, sorted values, and score.
  def ticketBatch(values: List[Int], code: Int, limit: Int): String =
    if (code == 777777) "priority-code"
    else if (values.length < 3) "small-batch"
    else {
      var sum  = 0L
      var rest = values
      while (rest.nonEmpty) {
        sum += rest.head.toLong
        if (rest.tail.nonEmpty && rest.head > rest.tail.head) return "unsorted"
        rest = rest.tail
      }

      val score = sum + limit.toLong * 2L
      if (score < 1000000L) "low-score"
      else if (score > 1001000L) "high-score"
      else "accepted"
    }

  // Classifies a value batch by bounds, order, overlap, and width.
  def batchWindow(values: List[Int], low: Int, high: Int): String =
    if (low == -1000000 || high == 1000000) "wide-open"
    else if (values.length < 3) "small-batch"
    else {
      var sorted  = true
      var overlap = false
      var rest    = values
      while (rest.nonEmpty) {
        if (rest.head >= low && rest.head <= high) overlap = true
        if (rest.tail.nonEmpty && rest.head > rest.tail.head) sorted = false
        rest = rest.tail
      }

      val width = high.toLong - low.toLong
      if (!sorted) "unsorted"
      else if (width < 1000L) "narrow"
      else if (width > 1000000L) "wide"
      else if (overlap) "overlap"
      else if (values.nonEmpty && values.last < low) "below"
      else "above"
    }
}
