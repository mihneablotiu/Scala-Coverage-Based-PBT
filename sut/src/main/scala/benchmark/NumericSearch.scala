package benchmark

object NumericSearch {

  def window(n: Int): String = {
    val lower = 499995
    val upper = 500005
    if (n <= lower) "below"
    else if (n >= upper) "above"
    else "inside"
  }

  def derivedEq(n: Int): String = {
    val target  = 1000000
    val doubled = 2 * n
    if (doubled < target) "low"
    else if (doubled > target) "high"
    else "exact"
  }

  def offsetEq(n: Int): String = {
    val shifted = n + 7
    if (shifted < 1000000) "low"
    else if (shifted > 1000000) "high"
    else "exact"
  }

  def band(x: Int, y: Int): String =
    if (x <= 1000 || x >= 1002) "x-outside"
    else if (y <= 2000 || y >= 2002) "y-outside"
    else "inside"

  def scaledOffset(n: Int): String = {
    val score = 3 * n + 7
    if (score < 1000000) "low"
    else if (score > 1000000) "high"
    else "exact"
  }
}
