package benchmark.bool

/** Boolean-input benchmark — the bottom of the difficulty ladder.
  *
  * The whole input space for `Boolean` is `{true, false}`, so any predicate over a single `Boolean`
  * has at most 2 inputs to cover. For tuples of `Boolean`s the space is `2ⁿ` and random saturates
  * essentially immediately. Used to anchor the framework on the simplest input type: if these don't
  * reach 100%, something is structurally broken.
  */
object BoolBench {

  /** No internal branch — one input "covers" the whole method. */
  def identity(b: Boolean): Boolean = b

  /** Single `if/else`. Two source branches. Saturates within 2 inputs. */
  def negate(b: Boolean): Boolean = if (b) false else true

  /** Multi-parameter showcase: 4 outcomes over a 3-Boolean space (`2³ = 8` inputs). Nested `if`
    * shape — outer 2 arms, inner 2 arms — so 4 source-level branches total. Random covers all
    * outcomes within ~15 inputs.
    */
  def threeAgree(a: Boolean, b: Boolean, c: Boolean): String =
    if (a == b && b == c) {
      if (a) "all-true" else "all-false"
    } else "mixed"
}
