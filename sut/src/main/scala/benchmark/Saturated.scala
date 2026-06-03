package benchmark

/** Random covers every arm in a handful of inputs — the calibration floor showing the guided strategies don't regress on easy code. */
object Saturated {

  def sign(n: Int): String =
    if (n > 0) "positive" else if (n < 0) "negative" else "zero"

  def headSign(xs: List[Int]): String = xs match {
    case Nil              => "empty"
    case h :: _ if h >= 0 => "head-non-negative"
    case _                => "head-negative"
  }

  def boolGate(a: Boolean, b: Boolean): String =
    if (a && b) "both" else if (a) "first" else if (b) "second" else "neither"

  def smallRange(n: Int): String =
    if (n > 5) "high" else if (n < -5) "low" else "middle"
}
