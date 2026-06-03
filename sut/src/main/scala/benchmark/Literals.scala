package benchmark

/** Branches gated by a *non-numeric* literal the input must equal — a string, an `Option` payload, a map key. Only the pool reaches these: the
  * gradient can't climb a string or an `Option`, so the pool mines the literal from the branch and injects it. Each method keeps one arm no literal
  * can help.
  */
object Literals {

  def accessLevel(s: String): String =
    if (s == "root") "superuser"
    else if (s == "admin") "privileged"
    else if (s.length >= 6 && s == s.reverse) "palindrome" // no literal helps
    else if (s.isEmpty) "anonymous"
    else "user"

  def httpMethod(s: String): String = s match {
    case "GET"    => "read"
    case "POST"   => "create"
    case "PUT"    => "update"
    case "DELETE" => "remove"
    case _        => "unknown"
  }

  def featureToggle(name: String): String = name match {
    case "dark-mode"   => "ui"
    case "beta-search" => "search"
    case "fast-path"   => "perf"
    case _             => "off"
  }

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
}
