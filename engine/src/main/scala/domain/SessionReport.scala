package domain

import java.nio.file.Path

/** Everything one fuzz session produces, in a form ready for any writer.
  *
  * Two coverage views live side-by-side:
  *
  *   - `sourceBranchCounter` — the authoritative source-level branch count from scoverage. Drives
  *     every "covered/total" headline (DOT picture, summary, growth chart y-axis).
  *   - `branchesByLine` — per-line JVM-branch counts from JaCoCo. Kept for the per-input feedback
  *     signal the guided strategy consumes and for the line-level breakdown in `summary.txt`. *Not*
  *     the headline number: JaCoCo counts bytecode branches, which inflate for `==` on reference
  *     types, `&&`/`||` short-circuits, and other constructs that desugar into multiple jumps.
  *
  * `coveredPositions` is populated post-session from a [[port.driven.SourceCoverageReader]]
  * (scoverage-backed by default) and is used by the writer to colour AST nodes accurately. It is
  * *not* used by the strategy or by any of the JaCoCo-derived counters above it.
  *
  * Parameterised over the input type `A`.
  */
final case class SessionReport[A](
    methodName: String,
    sourceFile: Path,
    totalInputs: Int,
    methodTree: Option[MethodTree],
    sourceBranchCounter: BranchCounter,
    branchesByLine: Map[Int, BranchSummary],
    inputLog: Vector[InputRecord[A]],
    growthCurve: Vector[Int],
    saturationInputIndex: Option[Int],
    coveredPositions: Set[Pos]
)
