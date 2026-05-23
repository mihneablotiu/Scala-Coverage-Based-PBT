package port.driven

import cats.effect.IO
import domain.MethodSourceCoverage

import java.nio.file.Path

/** Reads source-level coverage of one method in one source file.
  *
  * Backed by scoverage in the default implementation: the SUT is compiled with the scoverage
  * compiler plugin so every source-level branch is instrumented at compile time. Cheap enough
  * to call per fuzz iteration — the static statement map is cached, the measurement files are
  * small text logs.
  */
trait SourceCoverageReader {

  /** Source-level coverage snapshot for the given method at the moment of call. */
  def methodCoverage(sourceFile: Path, methodName: String): IO[MethodSourceCoverage]

  /** Writes a per-method TSV extract of scoverage's runtime data under
    * `by-method/<methodName>.measurements` for user inspection. Not used by the framework's data
    * path — `methodCoverage` reads scoverage live.
    */
  def splitMeasurementsByMethod(sourceFile: Path, methodName: String): IO[Unit]

  /** Removes stale runtime data so the first session sees a clean slate. Must be called **once,
    * before any SUT code runs in this JVM** — scoverage's `Invoker` caches `FileWriter`s after
    * the first SUT execution, so deleting files later would orphan them.
    */
  def cleanStaleData: IO[Unit]
}
