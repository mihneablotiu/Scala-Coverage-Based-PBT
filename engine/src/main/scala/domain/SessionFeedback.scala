package domain

/** Read-only view of everything observed so far in a fuzz session.
  *
  * Handed to the coverage-guided generator before each iteration so it can choose its next input
  * with full knowledge of what prior inputs exercised. Random ignores it.
  *
  * The use case also uses this type as its own running accumulator, so "loop state" and "what the
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
