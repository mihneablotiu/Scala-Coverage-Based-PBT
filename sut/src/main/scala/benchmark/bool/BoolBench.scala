package benchmark.bool

/** Boolean-input benchmark. Two trivial methods — sanity layer; random covers both within 2 inputs.
  * Establishes the lower anchor of the difficulty ladder.
  */
object BoolBench {

  /** No internal branch — always trivially "covered" after one input. */
  def identity(b: Boolean): Boolean = b

  /** Single `if`/`else`. Both arms reachable with 1 true + 1 false. */
  def negate(b: Boolean): Boolean = if (b) false else true
}
