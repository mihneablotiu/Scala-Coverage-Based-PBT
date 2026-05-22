package port.driven

import cats.effect.IO
import domain.MethodSourceCoverage

import java.nio.file.Path

/** Reads source-level coverage of one method in one source file.
  *
  * Backed by scoverage in the default implementation: the SUT is compiled with the scoverage
  * compiler plugin so every source-level branch is instrumented at compile time. Used by the use
  * case post-session to produce the headline branch counter and the writer's per-AST-node
  * colouring.
  */
trait SourceCoverageReader {

  /** Reads the per-method coverage snapshot. Requires `splitMeasurementsByMethod` to have run
    * first for `methodName` (otherwise returns [[MethodSourceCoverage.Empty]]).
    */
  def methodCoverage(sourceFile: Path, methodName: String): IO[MethodSourceCoverage]

  /** Extracts this method's slice of scoverage's runtime data into its own
    * `by-method/<methodName>.measurements` file. After this, [[methodCoverage]] reads from that
    * file alone — scoverage's shared `.measurements.*` files are no longer touched.
    *
    * The file path is derived purely from `methodName`, so adding new SUTs / new methods needs
    * no adapter changes.
    */
  def splitMeasurementsByMethod(sourceFile: Path, methodName: String): IO[Unit]

  /** Removes stale runtime data so the first session sees a clean slate. Implementations must
    * ensure this is a no-op after the first call — scoverage's `Invoker` caches `FileWriter`s by
    * data-dir, so deleting files mid-JVM would break subsequent writes.
    */
  def cleanStaleData: IO[Unit]
}
