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
  // Expected: targeted keeps the input closest to `2 * n == 1000000` by branch distance and mutates it, climbing toward the exact root one mutation step at a time.
  def derivedEq(n: Int): String = {
    val target  = 1000000
    val doubled = 2 * n
    if (doubled < target) "low"
    else if (doubled > target) "high"
    else "exact"
  }

  // Models an exact target behind a small offset.
  // Expected: targeted keeps the input closest to `n + 8 == 1000000` and mutates it; the mutator's `+8` step lines up with the offset, so a near input can cross exactly.
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
  // Expected: targeted keeps the closest `(x, y)` by branch distance and mutates it, with tuple mutation editing one coordinate while holding the other.
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
  // Expected: targeted keeps the input closest to `3 * n + 7 == 1000000` by branch distance and mutates it toward the threshold instead of treating the exact value as random chance.
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
