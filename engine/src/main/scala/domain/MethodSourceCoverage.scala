package domain

/** A scoverage snapshot of one method, in source-level terms.
  *
  *   - `coveredPositions` — every source position invoked at runtime (branches and non-branches).
  *     Drives the writer's per-AST-node green/red colouring.
  *   - `branchLines` — every source-level branch position in the method, paired with its source
  *     line. The complete set of "branches that exist", regardless of coverage.
  *
  * `coveredBranchPositions` is derived (intersection) and exposed because the use case computes it
  * per iteration to feed `SessionFeedback.append`.
  */
final case class MethodSourceCoverage(
    coveredPositions: Set[Pos],
    branchLines: Map[Pos, Int]
) {
  def coveredBranchPositions: Set[Pos] = coveredPositions.intersect(branchLines.keySet)
}
