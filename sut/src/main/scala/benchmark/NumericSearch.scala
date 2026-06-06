package benchmark

object NumericSearch {

  // Searches for a narrow integer window around a large value.
  def window(n: Int): String = {
    val lower = 499995
    val upper = 500005
    if (n <= lower) "below"
    else if (n >= upper) "above"
    else "inside"
  }

  // Searches for the value whose doubled score reaches the target.
  def derivedEq(n: Int): String = {
    val target  = 1000000
    val doubled = 2 * n
    if (doubled < target) "low"
    else if (doubled > target) "high"
    else "exact"
  }

  // Searches for the value that reaches the target after a small offset.
  def offsetEq(n: Int): String = {
    val shifted = n + 7
    if (shifted < 1000000) "low"
    else if (shifted > 1000000) "high"
    else "exact"
  }

  // Searches for roots whose square hits rare target values.
  def squareTarget(n: Int): String = {
    val squared = n * n
    if (squared == 49) "root-seven"
    else if (squared == 1000000) "root-million"
    else if (n < 0) "negative"
    else "other"
  }

  // Searches for two coordinates inside a narrow rectangular band.
  def band(x: Int, y: Int): String =
    if (x < 1000 || x > 1002) "x-outside"
    else if (y < 2000 || y > 2002) "y-outside"
    else "inside"

  // Searches for a relation between two integers and their difference.
  def difference(x: Int, y: Int): String = {
    val delta = x - y
    if (x > 1000000 && delta == 1000) "far-difference"
    else if (x + y == 0) "balanced"
    else if (delta < 0) "negative-delta"
    else "ordinary"
  }

  // Searches for the value whose scaled offset reaches the target.
  def scaledOffset(n: Int): String = {
    val score = 3 * n + 7
    if (score < 1000000) "low"
    else if (score > 1000000) "high"
    else "exact"
  }

  // Searches for a target product with overflow-safe arithmetic.
  def productBand(x: Int, y: Int): String = {
    val score = x.toLong * y.toLong + x.toLong - y.toLong
    if (score < 1000000L) "low"
    else if (score > 1001000L) "high"
    else if (score == 1000500L) "exact"
    else "near"
  }

  // Searches for values that satisfy a quadratic score window.
  def quadraticWindow(n: Int): String = {
    val score = n.toLong * n.toLong - 17L * n.toLong + 31L
    if (score < 250000L) "low"
    else if (score > 260000L) "high"
    else if (score == 255025L) "exact"
    else "near"
  }
}
