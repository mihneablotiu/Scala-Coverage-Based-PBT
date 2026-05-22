package benchmark.bool

/** Boolean-input benchmark — two trivial methods. The whole point of this file is to anchor the
  * bottom of the difficulty ladder: a `Gen.oneOf(true, false)` covers both arms within 2 inputs, so
  * any strategy (random or guided) saturates here essentially immediately. Used to confirm the
  * framework works end-to-end on the simplest possible input type.
  */
object BoolBench {

  /** No internal branch — one input "covers" the whole method. Baseline. */
  def identity(b: Boolean): Boolean = b

  /** Single 2-arm `if`. Saturates in 2 inputs under any strategy. */
  def negate(b: Boolean): Boolean = if (b) false else true
}
