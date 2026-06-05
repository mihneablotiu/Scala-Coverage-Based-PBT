package benchmark

import benchmark.data.Tree

object Calibration {

  def sign(n: Int): String =
    if (n > 0) "positive" else if (n < 0) "negative" else "zero"

  def optionShape(o: Option[Int]): String = o match {
    case None             => "none"
    case Some(n) if n > 0 => "positive"
    case Some(n) if n < 0 => "negative"
    case Some(_)          => "zero"
  }

  def listShape(xs: List[Int]): String = xs match {
    case Nil         => "empty"
    case _ :: Nil    => "single"
    case _ :: _ :: _ => "many"
  }

  def pairOrder(x: Int, y: Int): String =
    if (x < y) "lt" else if (x > y) "gt" else "eq"

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
