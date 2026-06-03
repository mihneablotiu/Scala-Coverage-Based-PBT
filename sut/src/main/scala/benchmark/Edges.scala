package benchmark

/** Floating-point edge values — `NaN`, ±∞ — that random almost never produces and the gradient can't climb to (distance to `NaN` is undefined). Only
  * mutation reaches them: its "interesting value" set includes these edges. The other arms are easy, so mutation's *exclusive* win is the edge arms.
  */
object Edges {

  def magnitude(x: Double): String =
    if (x.isNaN) "nan"
    else if (x.isInfinite) "infinite"
    else if (math.abs(x) < 1e-9) "near-zero"
    else if (math.abs(x) > 1e9) "huge"
    else "ordinary"

  def nearPi(x: Double): String =
    if (x.isNaN) "nan"
    else if (math.abs(x - 3.14159) < 0.0001) "near-pi"
    else if (x > 0) "positive"
    else "non-positive"

  def floatClass(x: Double): String =
    if (x.isNaN) "nan"
    else if (x == Double.PositiveInfinity) "pos-inf"
    else if (x == Double.NegativeInfinity) "neg-inf"
    else if (x == 0.0) "zero"
    else "normal"
}
