package domain

import java.nio.file.Path

/** Everything one fuzz session produces, in a form ready for any writer.
  *
  * `coveredPositions` is populated post-session from a [[port.driven.SourceCoverageReader]]
  * (scoverage-backed by default) and is used by the writer to colour AST nodes accurately. It is
  * *not* used by the strategy or by any of the JaCoCo-derived counters above it.
  */
final case class SessionReport(
    methodName: String,
    sourceFile: Path,
    totalInputs: Int,
    methodTree: Option[MethodTree],
    branchesByLine: Map[Int, BranchSummary],
    inputLog: Vector[InputRecord],
    growthCurve: Vector[Int],
    saturationInputIndex: Option[Int],
    coveredPositions: Set[Pos]
)
