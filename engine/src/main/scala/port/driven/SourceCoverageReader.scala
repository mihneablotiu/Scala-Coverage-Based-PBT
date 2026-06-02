package port.driven

import domain.Pos

import java.nio.file.Path

/** Returns the source offsets of every statement fired so far in the given file. The use case marks a leaf covered when one of these offsets falls
  * inside the leaf's span. Matching is by *file + span*, never by scoverage's method attribution (which is unreliable around nested `def`s), so the
  * tree and the coverage source need not agree on how statements are named.
  */
trait SourceCoverageReader {
  def coverage(sourceFile: Path): Set[Pos]
}
