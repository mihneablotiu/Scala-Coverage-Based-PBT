package port.driven

import cats.effect.IO
import domain.Pos

import java.nio.file.Path

/** Reads source-level coverage of one method: the set of source positions that scoverage has seen
  * fired so far. The use case intersects this set with the method's leaf positions (from the
  * [[domain.BranchTree]]) to get the actual "branches covered". Keeping that filter in the domain —
  * not in this port — means the adapter doesn't need to know what counts as a branch.
  *
  * Backed by scoverage in the default adapter: the SUT is compiled with the scoverage compiler
  * plugin so every statement is instrumented at compile time. Cheap enough to call per iteration —
  * the static statement map is cached, the measurement files are small text logs.
  */
trait SourceCoverageReader {
  def coverage(sourceFile: Path, methodName: String): IO[Set[Pos]]
}
