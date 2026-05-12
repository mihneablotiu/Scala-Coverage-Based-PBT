package port.driven

import cats.effect.IO
import domain.MethodSourceCoverage

import java.nio.file.Path

/** Reads source-level coverage of one method in one source file.
  *
  * Backed by scoverage in the default implementation: the SUT is compiled with the scoverage
  * compiler plugin so every source-level branch is instrumented at compile time. At session end
  * this port returns a snapshot covering:
  *
  *   - the headline branch counter (covered/total at source level — the thesis-relevant number),
  *   - the set of branch positions (used to detect drift against the Scalameta AST),
  *   - the set of invoked positions (used by the report writer for per-node colouring).
  *
  * This is read once after the fuzz loop finishes. It plays no role in the per-input feedback that
  * the [[InputGenerator]] sees — that comes from the bytecode-level [[BranchCoverageTracker]].
  */
trait SourceCoverageReader {

  def methodCoverage(sourceFile: Path, methodName: String): IO[MethodSourceCoverage]

  /** Removes stale runtime data so the first session sees a clean slate. Implementations should
    * ensure this is a no-op after the first call — scoverage's `Invoker` caches `FileWriter`s by
    * data-dir, so deleting files mid-JVM would break subsequent writes.
    */
  def cleanStaleData: IO[Unit]
}
