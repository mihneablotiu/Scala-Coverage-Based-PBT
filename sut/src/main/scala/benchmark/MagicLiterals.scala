package benchmark

import benchmark.data.Tree

object MagicLiterals {

  // Requires exact integer literals to reach the rare branches.
  def magicInt(n: Int): String =
    if (n == 987654321) "large-hit"
    else if (n == -123456789) "negative-hit"
    else "miss"

  // Requires exact floating-point literals to reach the rare branches.
  def magicDouble(d: Double): String =
    if (d == 3.14159) "pi"
    else if (d == -0.25) "quarter"
    else if (d == 0.0) "zero"
    else "other"

  // Requires exact string literals to reach privileged user paths.
  def magicString(s: String): String =
    if (s == "root") "superuser"
    else if (s == "admin") "privileged"
    else if (s == "guest") "limited"
    else if (s.isEmpty) "anonymous"
    else "user"

  // Requires exact boolean combinations to cover all states.
  def magicBoolean(enabled: Boolean, archived: Boolean): String =
    if (enabled == true && archived == false) "active"
    else if (enabled == true) "enabled"
    else if (archived == true) "archived"
    else "disabled"

  // Requires an exact integer literal inside an option.
  def magicOption(o: Option[Int]): String = o match {
    case Some(8080) => "service"
    case Some(443)  => "secure"
    case Some(_)    => "some"
    case None       => "none"
  }

  // Requires two exact coordinate literals to reach the deepest branch.
  def coords(x: Int, y: Int): String =
    if (x == 42) {
      if (y == 1000) "hit" else "x-only"
    } else "miss"

  // Requires an exact integer literal to appear inside a list.
  def whitelist(xs: List[Int]): String = {
    var found = false
    var rest  = xs
    while (rest.nonEmpty && !found) {
      if (rest.head == 31337) found = true
      rest = rest.tail
    }

    if (found) "hit" else "miss"
  }

  // Requires an exact integer literal to appear inside a tree.
  def treeMarker(t: Tree[Int]): String = {
    var found = false
    var stack = List(t)
    while (stack.nonEmpty && !found) {
      val current = stack.head
      stack = stack.tail
      current match {
        case Tree.Leaf          => found = found
        case Tree.Node(l, v, r) =>
          found = v == 65535
          stack = l :: r :: stack
      }
    }

    if (found) "hit" else "miss"
  }
}
