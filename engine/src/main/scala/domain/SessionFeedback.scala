package domain

/** Read-only view of everything observed so far in a fuzz session.
  *
  * The use case folds an immutable instance through a pure `step` function across iterations, so
  * "loop state" and "what the guided generator sees" are the same value. Parameterised over the
  * input type `A`.
  */
final case class SessionFeedback[A](
    history: Vector[InputRecord[A]],
    cumulativeCoverage: Map[Int, BranchCounter],
    hitCountsByLine: Map[Int, Int],
    firstHitsByLine: Map[Int, Int],
    growthCurve: Vector[Int]
) {
  def iteration: Int = history.size
}

object SessionFeedback {
  def empty[A]: SessionFeedback[A] =
    SessionFeedback[A](Vector.empty, Map.empty, Map.empty, Map.empty, Vector.empty)
}
