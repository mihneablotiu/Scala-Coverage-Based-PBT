package domain

/** Read-only view of everything observed so far in a fuzz session.
  *
  * Handed to an [[port.driven.InputGenerator]] before each iteration so a coverage-guided strategy
  * can choose its next input with full knowledge of what prior inputs exercised. Random strategies
  * simply ignore it.
  *
  * The fuzz loop also uses this type as its own running accumulator, so "loop state" and "what the
  * generator sees" are the same value. Parameterised over the input type `A`.
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
