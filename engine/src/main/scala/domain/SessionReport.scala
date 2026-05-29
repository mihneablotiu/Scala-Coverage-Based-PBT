package domain

import java.nio.file.Path

/** What one fuzz session produced.
  *
  * Pure data, five irreducible fields. Derived numbers (covered/total counts, saturation index, per-branch first-hit) are computed downstream by the
  * Python analysis scripts under `engine/reports/scripts/`.
  */
final case class SessionReport[A](
    methodName: String,
    sourceFile: Path,
    branchTree: Option[BranchTree],
    strategy: String,
    feedback: SessionFeedback[A]
)
