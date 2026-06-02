package benchmark

/** Tiny satisfying slices: narrow numeric bands and floating-point edge values, on Int / Long / Double. Random's hit probability is negligible;
  * mutation reaches the float edges (NaN/∞) while the pool supplies the literal bounds.
  */
object NarrowRanges {

  def tightBand(n: Int): String =
    if (n >= 1000 && n <= 1009) "in-band"
    else if (n > 0) "positive-out"
    else "non-positive"

  def longBand(n: Long): String =
    if (n >= 1000000000000L && n <= 1000000000009L) "in-band"
    else if (n > 0L) "positive"
    else "non-positive"

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
}
