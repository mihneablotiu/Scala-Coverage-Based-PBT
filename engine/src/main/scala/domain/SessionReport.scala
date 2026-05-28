package domain

import java.nio.file.Path

/** Everything one fuzz session produces, in a form ready for any writer.
  *
  * Five irreducible fields:
  *
  *   - `methodName`, `sourceFile` — identity of what was exercised.
  *   - `methodTree` — branchy AST of the method, when Scalameta could parse it (`None` if not).
  *   - `coverage` — scoverage snapshot read once at the end of the session.
  *   - `feedback` — per-iteration history accumulated by the loop.
  *
  * Every headline figure the writer emits (input count, branches covered / total, saturation index,
  * per-branch first-hit lookups) is derived from these two summaries — see the accessor methods
  * below. Keeping them out of the constructor keeps the data minimal *and* consistent: derived
  * numbers can never disagree with their source.
  *
  * Parameterised over the input type `A`.
  */
final case class SessionReport[A](
    methodName: String,
    sourceFile: Path,
    methodTree: Option[MethodTree],
    coverage: MethodSourceCoverage,
    feedback: SessionFeedback[A]
) {

  /** Total inputs the loop produced — `Test.Parameters.minSuccessfulTests` in practice. */
  def totalInputs: Int = feedback.iteration

  /** Source-level branches covered in this session. */
  def covered: Int = coverage.coveredBranchPositions.size

  /** Source-level branches that *exist* in this method, covered or not. */
  def total: Int = coverage.branchLines.size

  /** Input index at which the cumulative covered count first hit its final value, i.e. the point
    * beyond which no further input added a branch. `None` if the curve never rose above zero, which
    * would otherwise misleadingly report "saturated at input #0".
    */
  def saturation: Option[Int] = {
    val curve = feedback.growthCurve
    val finalCov = curve.lastOption.getOrElse(0)
    Option.when(finalCov > 0)(curve.indexOf(finalCov))
  }
}
