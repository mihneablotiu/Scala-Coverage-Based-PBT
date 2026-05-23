package port.driven

import domain.CoverageMeasurement

/** Runtime tracking of branch-level coverage for one method.
  *
  *   - `reset` clears any cumulative coverage state — call once per session.
  *   - `measure` returns both the per-input slice (since the previous call) and the cumulative
  *     state (since the last `reset`).
  */
trait BranchCoverageTracker {
  def reset(): Unit
  def measure(sourceFile: String, methodName: String): CoverageMeasurement
}
