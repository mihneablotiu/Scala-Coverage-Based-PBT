package domain

/** Two views of the same method's branch coverage produced together.
  *
  *   - `perInput` — what the *most recent* input alone exercised
  *   - `cumulative` — what every input so far has exercised, OR'd together
  *
  * Each is a per-line branch counter; the index is the source line, the value is "covered/total
  * branches at that line" as JaCoCo reports it.
  *
  * Note: per-individual-direction (then/else/case-i) attribution would need either JaCoCo's
  * internal `Instruction` API or a full bytecode-CFG reachability analysis. The line-level counter
  * is a faithful subset of what JaCoCo's stable public API exposes; it's what we use here.
  */
final case class CoverageMeasurement(
    perInput: Map[Int, BranchCounter],
    cumulative: Map[Int, BranchCounter]
)
