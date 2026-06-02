package benchmark

/** Two inputs must agree or relate. Independent random draws almost never coincide, so even the guided strategies struggle here — a clear "future
  * work" frontier. Same-type multi-parameter methods.
  */
object Relational {

  def compareInts(a: Int, b: Int): String =
    if (a == b) "equal"
    else if (math.abs(a) > 1000 && a == -b) "big-negatives"
    else if (b != 0 && a % b == 0) "a-multiple-of-b"
    else "unrelated"

  // size >= 4 so the "reverse" arm can't be reached by degenerate empty/singleton lists.
  def isReverseOf(xs: List[Int], ys: List[Int]): String =
    if (xs.size < 4 || ys.size < 4) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs == ys.reverse) "reverse"
    else "unrelated"

  // size >= 2 string keys: two random maps almost never share a key, let alone a whole keyset.
  def sameKeys(a: Map[String, Int], b: Map[String, Int]): String =
    if (a.size < 2 || b.size < 2) "trivial"
    else if (a.keySet == b.keySet) "same-keys"
    else if (a.keySet.subsetOf(b.keySet)) "a-subset-of-b"
    else "disjoint-ish"
}
