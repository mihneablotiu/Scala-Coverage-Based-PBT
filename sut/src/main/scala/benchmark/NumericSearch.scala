package benchmark

/** Numeric guards over *computed* values or *relations between* parameters — never a bare `input == literal`, so injecting the literal can't help (it
  * isn't the input). Only the branch-distance gradient reaches these, and only by climbing over many inputs.
  */
object NumericSearch {

  // x*x == t: the pool can inject t, but the input is x, so it must search the square root.
  def squareTarget(x: Int): String =
    if (x * x == 49) "root-of-49"
    else if (x * x == 1000000) "root-of-million"
    else if (x < 0) "negative"
    else "other"

  def cubeTarget(x: Int): String =
    if (x * x * x == 27) "cube-root-3"
    else if (x * x * x < 0) "negative-cube"
    else "other"

  // a 20-wide window a million up, then a divisibility step inside it — a gradual climb.
  def narrowWindow(n: Int): String =
    if (n > 1000000 && n < 1000020)
      if (n % 7 == 0) "in-window-div7" else "in-window"
    else if (n > 0) "positive"
    else "non-positive"

  // strict bounds: the endpoints 1000/1009 are *excluded*, so injecting them misses — the gradient must climb inside (1001..1008).
  def tightBand(n: Int): String =
    if (n > 1000 && n < 1009) "in-band"
    else if (n > 0) "positive-out"
    else "non-positive"

  // (a-b)² == t: a squared relation between two inputs. Injecting the target overshoots ((10⁶)² ≠ 10⁶); only the gradient, driving a-b to ±1000, reaches it.
  def product(a: Int, b: Int): String =
    if ((a - b) * (a - b) == 1000000) "sq-diff-million"
    else if (a * b < 0) "opposite-signs"
    else if (a * b == 0) "has-zero"
    else "other"

  // a - b == 1000, but gated above a million: pooled literals (1000, 0) can't satisfy `a > 1000000`, so only the gradient's climb lands here.
  def difference(a: Int, b: Int): String =
    if (a > 1000000 && a - b == 1000) "diff-1000"
    else if (a + b == 0) "sum-zero"
    else "other"

  def compareInts(a: Int, b: Int): String =
    if (a == b) "equal"
    else if (math.abs(a) > 1000 && a == -b) "big-negatives"
    else if (b != 0 && a % b == 0) "a-multiple-of-b"
    else "unrelated"

  // a*a + b*b == c*c: a Pythagorean triple over three inputs — the hardest numeric relation, still with a handle.
  def pythagorean(a: Int, b: Int, c: Int): String =
    if (a <= 0 || b <= 0 || c <= 0) "non-positive"
    else if (a * a + b * b == c * c) "triple"
    else if (a * a + b * b < c * c) "obtuse"
    else "acute"
}
