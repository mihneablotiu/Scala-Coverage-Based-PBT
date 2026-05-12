package port.driven

import cats.effect.IO
import domain.CoverageMeasurement

/** Runtime tracking of branch-level coverage for a single method.
  *
  *   - `reset` clears any cumulative coverage state — call once per session.
  *   - `measure` returns both the per-input slice (since the previous call) and the cumulative
  *     state (since the last `reset`).
  *
  * Implementations decide how the underlying probe machinery works (e.g. JaCoCo, our own ASM
  * instrumentation, etc.).
  */
trait BranchCoverageTracker {
  def reset: IO[Unit]
  def measure(sourceFile: String, methodName: String): IO[CoverageMeasurement]
}
