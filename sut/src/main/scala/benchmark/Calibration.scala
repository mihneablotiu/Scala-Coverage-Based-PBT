package benchmark

import benchmark.data.Tree

object Calibration {

  // Models a shallow integer sign split.
  // Expected: no guided tactic should dominate because `n > 0`, `n < 0`, and zero are simple ScalaCheck draws.
  def sign(n: Int): String =
    if (n > 0) "positive" else if (n < 0) "negative" else "zero"

  // Models the standard shape split of an optional integer.
  // Expected: no guided tactic should dominate because ScalaCheck already generates `None`, `Some`, and signed payloads.
  def optionShape(o: Option[Int]): String = o match {
    case None             => "none"
    case Some(n) if n > 0 => "positive"
    case Some(n) if n < 0 => "negative"
    case Some(_)          => "zero"
  }

  // Models only the size shape of a list.
  // Expected: no guided tactic should dominate because `Nil`, singleton, and longer lists are native random shapes.
  def listShape(xs: List[Int]): String = xs match {
    case Nil         => "empty"
    case _ :: Nil    => "single"
    case _ :: _ :: _ => "many"
  }

  // Models only the length shape of a string.
  // Expected: no guided tactic should dominate because empty, short, and longer strings are ordinary random cases.
  def stringShape(s: String): String =
    if (s.isEmpty) "empty"
    else if (s.length == 1) "single"
    else if (s.length == 2) "double"
    else "long"

  // Models the full truth table of two booleans.
  // Expected: no guided tactic should dominate because the four combinations are already dense in the random domain.
  def boolGate(a: Boolean, b: Boolean): String =
    if (a && b) "both" else if (a) "first" else if (b) "second" else "neither"

  // Models the order relation between two integers.
  // Expected: no guided tactic should dominate; `<` and `>` are broad random branches, while equality is the only sparse case.
  def pairOrder(x: Int, y: Int): String =
    if (x < y) "lt" else if (x > y) "gt" else "eq"

  // Models only the coarse shape of a generated tree.
  // Expected: no guided tactic should dominate because random tree generation already explores empty, shallow, and larger shapes.
  def treeShape(t: Tree[Int]): String = {
    var size     = 0
    var maxDepth = 0
    var stack    = List((t, 0))
    while (stack.nonEmpty) {
      val (current, depth) = stack.head
      stack = stack.tail
      current match {
        case Tree.Leaf          => ()
        case Tree.Node(l, _, r) =>
          size += 1
          maxDepth = math.max(maxDepth, depth + 1)
          stack = (l, depth + 1) :: (r, depth + 1) :: stack
      }
    }

    if (size == 0) "empty"
    else if (maxDepth <= 2) "small"
    else if (size >= 7) "large"
    else "skinny"
  }
}
