package benchmark

/** Control: every arm is reached by random within a handful of inputs — the calibration floor that shows the guided strategies don't regress on easy
  * code.
  */
object Saturated {

  def sign(n: Int): String =
    if (n > 0) "positive" else if (n < 0) "negative" else "zero"

  def headSign(xs: List[Int]): String = xs match {
    case Nil              => "empty"
    case h :: _ if h >= 0 => "head-non-negative"
    case _                => "head-negative"
  }
}
