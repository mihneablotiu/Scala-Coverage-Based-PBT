package benchmark

/** Deeply nested conditionals and conjunctions: reaching an inner arm needs several guards true at once, so the joint probability under random
  * sampling collapses. Guards are relations and ranges (no magic literal to inject), so the gradient must work for the inner arms.
  */
object DeepConditionals {

  def triangleType(a: Int, b: Int, c: Int): String =
    if (a <= 0 || b <= 0 || c <= 0) "invalid"
    else if (a + b <= c || a + c <= b || b + c <= a) "degenerate"
    else if (a == b && b == c) "equilateral"
    else if (a == b || b == c || a == c) "isosceles"
    else "scalene"

  // A real matrix (>= 4 equal-length rows of width >= 4) is something random lists almost never form.
  def gridShape(rows: List[List[Int]]): String =
    if (rows.size < 4) "too-few-rows"
    else if (rows.exists(_.size != rows.head.size)) "ragged"
    else {
      val width = rows.head.size
      if (width < 4) "thin"
      else if (rows.size == width) "square"
      else "rectangular"
    }

  // Mixed-type parameters; the "sum"/"k…" tags are string literals the pool can supply.
  def mixedClassify(n: Int, tag: String, xs: List[Int]): String =
    if (tag == "sum")
      if (xs.sum == n) "sum-matches" else "sum-differs"
    else if (tag.startsWith("k") && n > 0)
      if (xs.contains(n)) "contains-n" else "missing-n"
    else if (xs.isEmpty) "empty-list"
    else "other"
}
