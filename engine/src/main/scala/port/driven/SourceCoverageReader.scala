package port.driven

import cats.effect.IO
import domain.MethodSourceCoverage

import java.nio.file.Path

/** Reads source-level coverage of one method in one source file.
  *
  * Backed by scoverage in the default adapter: the SUT is compiled with the scoverage compiler
  * plugin so every branchy statement is instrumented at compile time. Cheap enough to call per
  * iteration — the static statement map is cached, the measurement files are small text logs.
  */
trait SourceCoverageReader {
  def coverage(sourceFile: Path, methodName: String): IO[MethodSourceCoverage]
}
