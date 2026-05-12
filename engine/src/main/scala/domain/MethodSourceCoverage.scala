package domain

/** Source-level coverage snapshot for one method, derived from scoverage's compile-time
  * instrumentation.
  *
  *   - `branchCounter` — total source-level branches in the method and how many were invoked. This
  *     is the authoritative headline number: it counts what *scalac actually branches on at the
  *     source level*, independent of how those constructs compile down to JVM bytecode.
  *   - `branchPositions` — character offset of every source-level branch statement. Used to detect
  *     drift against the Scalameta [[BranchTree]]: if scoverage knows about branch positions that
  *     the AST builder didn't enumerate, the visual is incomplete and `BranchTree` needs a new
  *     variant.
  *   - `coveredPositions` — every source position invoked at runtime (branch or not). Drives the
  *     writer's per-AST-node green/red colouring.
  */
final case class MethodSourceCoverage(
    branchCounter: BranchCounter,
    branchPositions: Set[Pos],
    coveredPositions: Set[Pos]
)

object MethodSourceCoverage {
  val Empty: MethodSourceCoverage =
    MethodSourceCoverage(BranchCounter.Zero, Set.empty, Set.empty)
}
