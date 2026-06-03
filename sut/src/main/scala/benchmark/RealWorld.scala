package benchmark

/** Real, faithfully-ported algorithms — not written to suit our tactics. They show the honest spread of real code: numeric rules whose arms random
  * reaches on its own, and a parser whose interesting arms sit behind a structural gate that no tactic can target (the frontier — and the future
  * target for validity-guided generation).
  */
object RealWorld {

  // The Gregorian leap-year rule. Modular guards — random covers them; here to show real code that isn't hard.
  def leapYear(year: Int): String =
    if (year % 400 == 0) "leap-century"
    else if (year % 100 == 0) "common-century"
    else if (year % 4 == 0) "leap"
    else "common"

  // IPv4 validation: every arm past the first sits behind "split into exactly 4 numeric parts" — a derived structural gate. Frontier.
  def isValidIp(s: String): String = {
    val parts = s.split('.')
    if (parts.length != 4) "wrong-part-count"
    else if (parts.exists(_.isEmpty)) "empty-part"
    else if (parts.exists(p => !p.forall(_.isDigit))) "non-numeric"
    else if (parts.exists(p => p.toInt > 255)) "out-of-range"
    else "valid"
  }
}
