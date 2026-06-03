package benchmark

/** One method, several arms — each reachable by a *different* tactic. A single strategy covers only its own arm; only a composite covers them all, so
  * these are where "you need more than one tactic" shows up *within a single function*.
  *
  * The numeric (gradient) arm comes first so its path is purely numeric and the gradient can target it; the string (pool) and float-edge (mutation)
  * arms follow — a literal gate behind a numeric one stays poolable, and an edge value is reached by mutation regardless of the path.
  */
object Mixed {

  // gradient arm (n*n == 49) + pool arm (tag) + random arm.
  def classifyCode(tag: String, n: Int): String =
    if (n * n == 49) "square-49"      // gradient
    else if (tag == "magic") "tagged" // pool
    else if (n < -1000000) "very-low" // random
    else "ordinary"

  // mutation arm (NaN) + pool arm (tag) + random arm.
  def classifyFloat(tag: String, x: Double): String =
    if (x.isNaN) "nan"                  // mutation
    else if (tag == "special") "tagged" // pool
    else if (x < 0) "negative"          // random
    else "ordinary"

  // gradient + mutation + pool arms in one method — only the all-three composite covers them all.
  def triage(tag: String, n: Int, x: Double): String =
    if (n * n == 49) "square-49"      // gradient
    else if (x.isInfinite) "infinite" // mutation
    else if (tag == "alert") "tagged" // pool
    else "ordinary"
}
