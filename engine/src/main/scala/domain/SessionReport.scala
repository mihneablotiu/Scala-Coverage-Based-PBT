package domain

import java.nio.file.Path

/** Everything one fuzz session produces, in a form ready for any writer.
  *
  * Five irreducible fields:
  *
  *   - `methodName`, `sourceFile` — identity of what was exercised.
  *   - `methodTree` — branchy AST of the method, when Scalameta could parse it (`None` if not). The
  *     leaves of this tree are the canonical "branches" for coverage purposes.
  *   - `coveredPositions` — every source position scoverage saw fired during the session. Used by
  *     the writer to colour every node in the DOT graph; *not* used directly for branch counting
  *     (the use case has already intersected it with the tree's leaves to populate `feedback`).
  *   - `feedback` — per-iteration history accumulated by the loop. Its `newlyCoveredBranches` sets
  *     are leaf positions only — the use case filters them before `append`.
  *
  * Every headline figure the writer emits (input count, branches covered / total, saturation index,
  * per-branch first-hit lookups) is derived from these — see the accessor methods below. Keeping
  * them out of the constructor keeps the data minimal *and* consistent: derived numbers can never
  * disagree with their source.
  *
  * Parameterised over the input type `A`.
  */
final case class SessionReport[A](
    methodName: String,
    sourceFile: Path,
    methodTree: Option[MethodTree],
    coveredPositions: Set[Pos],
    feedback: SessionFeedback[A]
) {

  /** Total inputs the loop produced — `Test.Parameters.minSuccessfulTests` in practice. */
  def totalInputs: Int = feedback.iteration

  /** Source-level leaves of this method that scoverage saw fired during the session. */
  def covered: Int = feedback.coveredBranches.size

  /** Source-level leaves the method has, covered or not. `0` when Scalameta couldn't parse the
    * source — at which point neither `covered` nor `total` carries meaning.
    */
  def total: Int = methodTree.fold(0)(t => BranchTree.leaves(t.body).size)

  /** Input index at which the cumulative covered count first hit its final value, i.e. the point
    * beyond which no further input added a leaf. `None` if the curve never rose above zero, which
    * would otherwise misleadingly report "saturated at input #0".
    */
  def saturation: Option[Int] = {
    val curve = feedback.growthCurve
    val finalCov = curve.lastOption.getOrElse(0)
    Option.when(finalCov > 0)(curve.indexOf(finalCov))
  }
}
