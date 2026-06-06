package benchmark

import benchmark.data.Tree

object MagicLiterals {

  // Models exact integer gates.
  // Expected: pool/pool-mutation should help because `n == 987654321` and `n == -123456789` use literals mined from this method.
  def magicInt(n: Int): String =
    if (n == 987654321) "large-hit"
    else if (n == -123456789) "negative-hit"
    else "miss"

  // Models exact floating-point gates.
  // Expected: pool/pool-mutation should help because `3.14159`, `-0.25`, and `0.0` are mined doubles; random Double almost never hits them.
  def magicDouble(d: Double): String =
    if (d == 3.14159) "pi"
    else if (d == -0.25) "quarter"
    else if (d == 0.0) "zero"
    else "other"

  // Models exact string role gates.
  // Expected: pool/pool-mutation should help because `"root"`, `"admin"`, and `"guest"` are mined strings; random strings rarely match them.
  def magicString(s: String): String =
    if (s == "root") "superuser"
    else if (s == "admin") "privileged"
    else if (s == "guest") "limited"
    else if (s.isEmpty) "anonymous"
    else "user"

  // Models exact boolean combinations.
  // Expected: this validates boolean pooling, but random should also do well because the boolean domain has only four pairs.
  def magicBoolean(enabled: Boolean, archived: Boolean): String =
    if (enabled == true && archived == false) "active"
    else if (enabled == true) "enabled"
    else if (archived == true) "archived"
    else "disabled"

  // Models exact service-port payloads inside an option.
  // Expected: pool/pool-mutation should help because `Some(8080)` and `Some(443)` require mined integer payloads inside `Some`.
  def magicOption(o: Option[Int]): String = o match {
    case Some(8080) => "service"
    case Some(443)  => "secure"
    case Some(_)    => "some"
    case None       => "none"
  }

  // Models a nested exact coordinate gate.
  // Expected: pool/pool-mutation should help because the deepest branch needs both mined literals, `x == 42` and then `y == 1000`.
  def coords(x: Int, y: Int): String =
    if (x == 42) {
      if (y == 1000) "hit" else "x-only"
    } else "miss"

  // Models an exact whitelist marker inside a list.
  // Expected: pool/pool-mutation should help because list pooling can place mined literal `31337` into the generated list.
  def whitelist(xs: List[Int]): String = {
    var found = false
    var rest  = xs
    while (rest.nonEmpty && !found) {
      if (rest.head == 31337) found = true
      rest = rest.tail
    }

    if (found) "hit" else "miss"
  }

  // Models an exact marker inside a tree.
  // Expected: pool/pool-mutation should help because tree pooling can create nodes containing mined literal `65535`.
  def treeMarker(t: Tree[Int]): String = {
    var found = false
    var stack = List(t)
    while (stack.nonEmpty && !found) {
      val current = stack.head
      stack = stack.tail
      current match {
        case Tree.Leaf          => ()
        case Tree.Node(l, v, r) =>
          found = v == 65535
          stack = l :: r :: stack
      }
    }

    if (found) "hit" else "miss"
  }
}
