package domain

/** A scoverage snapshot of one method, all in source-level terms.
  *
  *   - `coveredPositions` — every source position invoked at runtime (branches *and* non-branches).
  *     Drives the writer's per-AST-node green/red colouring in the picture.
  *   - `branchLines` — every source-level branch position in the method, paired with its source
  *     line. The complete set of "branches that exist", regardless of coverage.
  *
  * `coveredBranchPositions` and `branchCounter` are derived — keeping them out of the constructor
  * makes the data clearly minimal: scoverage gives us "what fired" and "what exists", we compute
  * the rest.
  */
final case class MethodSourceCoverage(
    coveredPositions: Set[Pos],
    branchLines: Map[Pos, Int]
) {

  /** Branch positions actually covered — the intersection of "fired positions" and "branches". */
  def coveredBranchPositions: Set[Pos] = coveredPositions.intersect(branchLines.keySet)

  /** Headline figure: `covered / total` source branches in this method. */
  def branchCounter: BranchCounter =
    BranchCounter(covered = coveredBranchPositions.size, total = branchLines.size)
}

object MethodSourceCoverage {
  val Empty: MethodSourceCoverage =
    MethodSourceCoverage(Set.empty, Map.empty)
}
