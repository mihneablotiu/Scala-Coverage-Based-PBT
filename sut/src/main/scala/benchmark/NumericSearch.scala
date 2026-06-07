package benchmark

object NumericSearch {

  // Models a narrow window around mined numeric boundaries.
  // Expected: pool-mutation should help because pool can draw `499995`/`500005`, then Int mutation offsets can step inside the window.
  def window(n: Int): String = {
    val lower = 499995
    val upper = 500005
    if (n <= lower) "below"
    else if (n >= upper) "above"
    else "inside"
  }

  // Models an exact target reached through multiplication.
  // Expected: targeted should help because branch distance sees how far `2 * n` is from `1000000`, and its half-distance candidate matches the coefficient.
  def derivedEq(n: Int): String = {
    val target  = 1000000
    val doubled = 2 * n
    if (doubled < target) "low"
    else if (doubled > target) "high"
    else "exact"
  }

  // Models an exact target behind a small offset.
  // Expected: targeted should help because the distance for `n + 8 == 1000000` is exactly the remaining offset from the current input.
  def offsetEq(n: Int): String = {
    val shifted = n + 8
    if (shifted < 1000000) "low"
    else if (shifted > 1000000) "high"
    else "exact"
  }

  // Models exact square targets.
  // Expected: this is mostly a limitation case because neither pool nor mutation directly inverts `n * n`; only small roots like `7` are easy.
  def squareTarget(n: Int): String = {
    val squared = n * n
    if (squared == 49) "root-seven"
    else if (squared == 1000000) "root-million"
    else if (n < 0) "negative"
    else "other"
  }

  // Models a two-coordinate narrow rectangular band.
  // Expected: targeted should help because each tuple field maps to one numeric argument, so branch distance can keep the closest `(x, y)` attempt.
  def band(x: Int, y: Int): String =
    if (x < 1000 || x > 1002) "x-outside"
    else if (y < 2000 || y > 2002) "y-outside"
    else "inside"

  // Models a relation between two integers and their difference.
  // Expected: targeted may help because it scores both `x > 1000000` and `delta == 1000`, while tuple mutation can preserve one coordinate and adjust the other.
  def difference(x: Int, y: Int): String = {
    val delta = x - y
    if (x > 1000000 && delta == 1000) "far-difference"
    else if (x + y == 0) "balanced"
    else if (delta < 0) "negative-delta"
    else "ordinary"
  }

  // Models a target hidden behind a scaled offset.
  // Expected: targeted should help because branch distance moves candidates toward the `3 * n + 7` threshold instead of treating the exact value as random chance.
  def scaledOffset(n: Int): String = {
    val score = 3 * n + 7
    if (score < 1000000) "low"
    else if (score > 1000000) "high"
    else "exact"
  }

  // Models a target product with overflow-safe arithmetic.
  // Expected: this remains a limitation case because targeted can score the product expression but does not factorize `x * y + x - y`.
  def productBand(x: Int, y: Int): String = {
    val score = x.toLong * y.toLong + x.toLong - y.toLong
    if (score < 1000000L) "low"
    else if (score > 1001000L) "high"
    else if (score == 1000500L) "exact"
    else "near"
  }

  // Models a quadratic score window.
  // Expected: this remains a limitation case because targeted can score the quadratic expression but does not invert it to select roots directly.
  def quadraticWindow(n: Int): String = {
    val score = n.toLong * n.toLong - 17L * n.toLong + 31L
    if (score < 250000L) "low"
    else if (score > 260000L) "high"
    else if (score == 255025L) "exact"
    else "near"
  }
}
