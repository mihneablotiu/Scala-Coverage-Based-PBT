package domain

/** Read-only view of everything observed so far in a fuzz session, in source-level terms.
  *
  * The use case folds an immutable instance through a pure `step` function across iterations, so
  * "loop state" and "what the guided generator sees" are the same value. Parameterised over the
  * input type `A`.
  *
  *   - `history` — one [[InputRecord]] per iteration, in order. Each record's
  *     `newlyCoveredBranches` is the input's incremental contribution.
  *   - `coveredBranches` — running set of source-branch positions covered so far. Used by `step` to
  *     compute the per-iteration delta against scoverage's cumulative state.
  *   - `growthCurve` — source-branch count after each iteration (cumulative). Drives `growth.svg`
  *     and the saturation index.
  */
final case class SessionFeedback[A](
    history: Vector[InputRecord[A]],
    coveredBranches: Set[Pos],
    growthCurve: Vector[Int]
) {
  def iteration: Int = history.size
}

object SessionFeedback {
  def empty[A]: SessionFeedback[A] =
    SessionFeedback[A](Vector.empty, Set.empty, Vector.empty)
}
