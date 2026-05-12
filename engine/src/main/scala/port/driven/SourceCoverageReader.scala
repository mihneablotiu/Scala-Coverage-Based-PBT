package port.driven

import cats.effect.IO
import domain.Pos

import java.nio.file.Path

/** Reads per-AST-position coverage of one method in one source file.
  *
  * Backed by scoverage in the default implementation: the SUT is compiled with the scoverage
  * compiler plugin so every meaningful AST node is instrumented; at session end this port reports
  * which positions inside the target method were invoked at runtime.
  *
  * This is only consumed by the report writer for accurate per-node colouring. It plays no role in
  * the main fuzz loop or the strategy.
  */
trait SourceCoverageReader {

  /** Source-file character offsets, inside the given `methodName` of the given `sourceFile`, that
    * were invoked at least once since the JVM started.
    */
  def coveredPositions(sourceFile: Path, methodName: String): IO[Set[Pos]]

  /** Removes stale runtime data so the first session sees a clean slate. Implementations should
    * ensure this is a no-op after the first call — scoverage's `Invoker` caches `FileWriter`s by
    * data-dir, so deleting files mid-JVM would break subsequent writes.
    */
  def cleanStaleData: IO[Unit]
}
