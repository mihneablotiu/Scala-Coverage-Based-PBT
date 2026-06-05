package benchmark

import benchmark.data.Tree

object MagicLiterals {

  def magicInt(n: Int): String =
    if (n == 987654321) "hit" else "miss"

  def magicOption(o: Option[Int]): String = o match {
    case Some(8080) => "service"
    case Some(_)    => "some"
    case None       => "none"
  }

  def coords(x: Int, y: Int): String =
    if (x == 42) {
      if (y == 1000) "hit" else "x-only"
    } else "miss"

  def whitelist(xs: List[Int]): String = {
    var found = false
    var rest  = xs
    while (rest.nonEmpty && !found) {
      if (rest.head == 31337) found = true
      rest = rest.tail
    }

    if (found) "hit" else "miss"
  }

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
