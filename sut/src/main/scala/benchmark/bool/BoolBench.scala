package benchmark.bool

/** Boolean-input benchmark — the bottom of the difficulty ladder. The full input space for `Boolean` is `{true, false}`, so any predicate saturates
  * within a handful of inputs.
  */
object BoolBench {

  def negate(b: Boolean): Boolean = if (b) false else true

  def threeAgree(a: Boolean, b: Boolean, c: Boolean): String =
    if (a == b && b == c) {
      if (a) "all-true" else "all-false"
    } else "mixed"
}
