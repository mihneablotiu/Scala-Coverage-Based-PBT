package domain

import java.nio.file.Path

/** Everything one fuzz session produces, in a form ready for any writer. Source-level throughout —
  * every covered/total figure, per-branch entry, and per-input row is expressed in scoverage's
  * source-statement units, never in bytecode-branch counts.
  *
  *   - `sourceBranchCounter` — authoritative source-level branch count from scoverage. Headline
  *     figure for every artifact.
  *   - `branches` — one row per source-level branch in the method, with its line and the iteration
  *     that first covered it (or `None` if it was never covered).
  *   - `coveredPositions` — every source position invoked, branch or not. Used by the writer to
  *     colour AST leaves accurately in the picture.
  *   - `inputLog` — per-input newly-covered branches; drives `inputs.csv`.
  *
  * Parameterised over the input type `A`.
  */
final case class SessionReport[A](
    methodName: String,
    sourceFile: Path,
    totalInputs: Int,
    methodTree: Option[MethodTree],
    sourceBranchCounter: BranchCounter,
    branches: Vector[BranchOutcome],
    inputLog: Vector[InputRecord[A]],
    growthCurve: Vector[Int],
    saturationInputIndex: Option[Int],
    coveredPositions: Set[Pos]
)
