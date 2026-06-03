package benchmark

import benchmark.data.Tree

/** The open frontier: arms guarded by properties with no handle for any tactic — a parse succeeding, a list being sorted, a valid BST, two inputs
  * relating. Every strategy degrades to random here, which essentially never satisfies them. These are the target for future validity-guided search.
  */
object Frontier {

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

  def isValidIp(s: String): String = {
    val parts = s.split('.')
    if (parts.length != 4) "wrong-part-count"
    else if (parts.exists(_.isEmpty)) "empty-part"
    else if (parts.exists(p => !p.forall(_.isDigit))) "non-numeric"
    else if (parts.exists(p => p.toInt > 255)) "out-of-range"
    else "valid"
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

  def isStrictlySorted(xs: List[Int]): String =
    if (xs.size < 8) "too-short"
    else if (xs.lazyZip(xs.tail).forall(_ < _)) "strictly-sorted"
    else if (xs.lazyZip(xs.tail).forall(_ <= _)) "non-decreasing"
    else "unsorted"

  def bstShape(t: Tree): String = {
    def isBst(node: Tree, lo: Int, hi: Int): Boolean = node match {
      case Tree.Leaf          => true
      case Tree.Node(l, v, r) => v > lo && v < hi && isBst(l, lo, v) && isBst(r, v, hi)
    }
    def size(node: Tree): Int = node match {
      case Tree.Leaf          => 0
      case Tree.Node(l, _, r) => 1 + size(l) + size(r)
    }
    t match {
      case Tree.Leaf => "empty"
      case _         =>
        if (!isBst(t, Int.MinValue, Int.MaxValue)) "not-bst"
        else if (size(t) >= 8) "large-bst"
        else "small-bst"
    }
  }

  def isReverseOf(xs: List[Int], ys: List[Int]): String =
    if (xs.size < 4 || ys.size < 4) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs == ys.reverse) "reverse"
    else "unrelated"

  def sameKeys(a: Map[String, Int], b: Map[String, Int]): String =
    if (a.size < 2 || b.size < 2) "trivial"
    else if (a.keySet == b.keySet) "same-keys"
    else if (a.keySet.subsetOf(b.keySet)) "a-subset-of-b"
    else "disjoint-ish"

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
