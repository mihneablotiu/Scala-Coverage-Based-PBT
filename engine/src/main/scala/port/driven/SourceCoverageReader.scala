package port.driven

import domain.Pos

import java.nio.file.Path

/** Returns the source positions that have been fired so far for the named method.
  *
  * The use case intersects this set with the method's leaf positions to decide what counts as a "covered branch", keeping the branch definition in
  * the domain rather than the adapter.
  */
trait SourceCoverageReader {
  def coverage(sourceFile: Path, methodName: String): Set[Pos]
}
