package benchmark

import benchmark.data.Tree

object Calibration {

  // Classifies an integer by its sign.
  def sign(n: Int): String =
    if (n > 0) "positive" else if (n < 0) "negative" else "zero"

  // Separates absent options from positive, negative, and zero payloads.
  def optionShape(o: Option[Int]): String = o match {
    case None             => "none"
    case Some(n) if n > 0 => "positive"
    case Some(n) if n < 0 => "negative"
    case Some(_)          => "zero"
  }

  // Classifies a list only by its size shape.
  def listShape(xs: List[Int]): String = xs match {
    case Nil         => "empty"
    case _ :: Nil    => "single"
    case _ :: _ :: _ => "many"
  }

  // Classifies a string only by its length shape.
  def stringShape(s: String): String =
    if (s.isEmpty) "empty"
    else if (s.length == 1) "single"
    else if (s.length == 2) "double"
    else "long"

  // Covers the four combinations of two boolean flags.
  def boolGate(a: Boolean, b: Boolean): String =
    if (a && b) "both" else if (a) "first" else if (b) "second" else "neither"

  // Compares the order relation between two integers.
  def pairOrder(x: Int, y: Int): String =
    if (x < y) "lt" else if (x > y) "gt" else "eq"

  // Classifies a generated tree by size and depth.
  def treeShape(t: Tree[Int]): String = {
    var size     = 0
    var maxDepth = 0
    var stack    = List((t, 0))
    while (stack.nonEmpty) {
      val (current, depth) = stack.head
      stack = stack.tail
      current match {
        case Tree.Leaf          => size += 0
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
