package domain

/** Source-level coverage snapshot for one method, derived from scoverage.
  *
  *   - `branchCounter` — total source-level branches in the method and how many fired. The headline
  *     number in `summary.txt`.
  *   - `coveredPositions` — every source position invoked at runtime (branch or not). Drives the
  *     writer's per-AST-node green/red colouring.
  */
final case class MethodSourceCoverage(
    branchCounter: BranchCounter,
    coveredPositions: Set[Pos]
)

object MethodSourceCoverage {
  val Empty: MethodSourceCoverage =
    MethodSourceCoverage(BranchCounter(0, 0), Set.empty)
}
