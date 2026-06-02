package benchmark

/** Equality guards against specific literals, across Int / String / Long / Option / Map / Boolean. Random almost never produces the literal, so those
  * arms stay cold; the `*-pool` strategies mine the literals from the source and walk straight in — the clearest win for coverage-guided generation.
  */
object MagicConstants {

  def classify(n: Int): String = n match {
    case 0                => "zero"
    case 42               => "answer"
    case x if x < 0       => "negative"
    case x if x > 1000000 => "big"
    case _                => "small-positive"
  }

  def accessLevel(s: String): String =
    if (s == "root") "superuser"
    else if (s == "admin") "privileged"
    else if (s.isEmpty) "anonymous"
    else "user"

  def luckyLong(n: Long): String =
    if (n == 8675309L) "jenny"
    else if (n == 9999999999L) "ten-nines"
    else if (n < 0L) "negative"
    else "ordinary"

  def routeOption(o: Option[Int]): String = o match {
    case None             => "none"
    case Some(42)         => "answer"
    case Some(n) if n < 0 => "negative"
    case Some(_)          => "other"
  }

  def lookupRole(roles: Map[String, Int]): String =
    roles.get("admin") match {
      case Some(level) if level >= 5 => "admin-high"
      case Some(_)                   => "admin-low"
      case None                      => "no-admin"
    }

  def gatedMagic(enabled: Boolean, code: Int): String =
    if (!enabled) "disabled"
    else if (code == 1337) "elite"
    else if (code == 9999) "near-special"
    else if (code < 0) "negative"
    else "ordinary"
}
