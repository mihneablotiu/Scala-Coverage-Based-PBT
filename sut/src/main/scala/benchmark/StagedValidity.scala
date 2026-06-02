package benchmark

/** Two-stage "parse then check": a raw input must first be well-formed, then satisfy a semantic condition, before the interesting arm runs. Random
  * inputs rarely pass even the first gate — the hardest group (Zest's domain). Exercises a `while` loop, a `for`-comprehension with a guard, a
  * mixed-type method, and two small algorithms (bracket matching, Luhn checksum).
  */
object StagedValidity {

  def parseVersion(s: String): String =
    if (!s.startsWith("v")) "not-versioned"
    else
      s.drop(1).split('.') match {
        case Array(maj, min) if maj.forall(_.isDigit) && min.forall(_.isDigit) =>
          maj.toIntOption match {
            case Some(0)           => "pre-release"
            case Some(m) if m >= 2 => "major-2-plus"
            case Some(_)           => "v1"
            case None              => "out-of-range"
          }
        case _ => "malformed"
      }

  def parseSignedInt(s: String): String =
    if (s.isEmpty) "empty"
    else {
      val negative = s.head == '-'
      val digits   = if (negative) s.drop(1) else s
      var i        = 0
      var ok       = digits.nonEmpty
      while (i < digits.length) {
        if (!digits.charAt(i).isDigit) ok = false
        i += 1
      }
      if (!ok) "not-an-int"
      else if (negative) "negative-int"
      else "non-negative-int"
    }

  def elementAt(xs: List[Int], i: Int): String =
    if (i < 0 || i >= xs.size) "out-of-range"
    else {
      val positives = (for (x <- xs if x > 0) yield x).size
      xs(i) match {
        case 42                        => "found-magic"
        case _ if positives == xs.size => "all-positive"
        case v if v < 0                => "negative-here"
        case _                         => "ordinary"
      }
    }

  def balancedBrackets(s: String): String = {
    var depth = 0
    var ok    = true
    var i     = 0
    while (i < s.length && ok) {
      val c = s.charAt(i)
      if (c == '(') depth += 1
      else if (c == ')') {
        depth -= 1
        if (depth < 0) ok = false
      }
      i += 1
    }
    if (!ok) "unbalanced-close"
    else if (depth > 0) "unclosed"
    else if (s.isEmpty) "empty"
    else if (s.forall(c => c == '(' || c == ')')) "all-brackets-balanced"
    else "balanced-with-other"
  }

  def luhnCheck(digits: List[Int]): String =
    if (digits.size < 4) "too-short"
    else if (digits.exists(d => d < 0 || d > 9)) "not-digits"
    else {
      val sum = digits.reverse.zipWithIndex.foldLeft(0) { case (acc, (d, idx)) =>
        if (idx % 2 == 1) {
          val doubled = d * 2
          acc + (if (doubled > 9) doubled - 9 else doubled)
        } else acc + d
      }
      if (sum % 10 == 0) "valid-luhn" else "invalid-luhn"
    }
}
