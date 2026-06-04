package benchmark

/** A tiny system under test — just enough branchy methods to exercise the framework end to end. The real benchmark suite comes later. */
object Saturated {

  def sign(n: Int): String =
    if (n > 0) "positive" else if (n < 0) "negative" else "zero"

  def headSign(xs: List[Int]): String = xs match {
    case Nil              => "empty"
    case h :: _ if h >= 0 => "head-non-negative"
    case _                => "head-negative"
  }

  def size(o: Option[Int]): String = o match {
    case None    => "none"
    case Some(_) => "some"
  }
}
