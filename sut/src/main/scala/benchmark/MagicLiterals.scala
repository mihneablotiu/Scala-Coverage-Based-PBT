package benchmark

/** The pool's niche, kept deliberately small: a few branches gated by a literal that *is* the input, so mining-and-injecting reaches them. Each
  * method also has an arm no literal can help (a derived or structural property), so the pool alone never reaches 100%.
  */
object MagicLiterals {

  def dispatch(code: Int): String = code match {
    case 200                                   => "ok"
    case 404                                   => "not-found"
    case 31337                                 => "elite"
    case c if c < 0                            => "negative"
    case c if c.toString == c.toString.reverse => "palindrome" // derived: no literal helps
    case _                                     => "other"
  }

  def accessLevel(s: String): String =
    if (s == "root") "superuser"
    else if (s == "admin") "privileged"
    else if (s.length >= 6 && s == s.reverse) "palindrome" // structural: no literal helps, random never makes one
    else if (s.isEmpty) "anonymous"
    else "user"
}
