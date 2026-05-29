package domain

/** Read-only view of one fuzz session so far.
  *
  * The irreducible field is `history`; `coveredBranches` and `growthCurve` are derived (`lazy val` because both the writer and `append`'s delta
  * computation touch them).
  */
final case class SessionFeedback[A](history: Vector[SessionFeedback.InputRecord[A]]) {

  def iteration: Int = history.size

  lazy val coveredBranches: Set[Pos] =
    history.iterator.flatMap(_.newlyCoveredBranches).toSet

  lazy val growthCurve: Vector[Int] =
    history.scanLeft(0)((c, r) => c + r.newlyCoveredBranches.size).tail

  /** `nowCovered` is the cumulative set of leaf positions fired so far (the use case intersects scoverage's hits with the method's leaves before
    * calling). The stored record keeps only the delta against the running `coveredBranches`.
    */
  def append(input: A, nowCovered: Set[Pos]): SessionFeedback[A] =
    SessionFeedback(
      history :+ SessionFeedback.InputRecord(iteration, input, nowCovered -- coveredBranches)
    )
}

object SessionFeedback {

  def empty[A]: SessionFeedback[A] = SessionFeedback(Vector.empty)

  /** One iteration's record. `newlyCoveredBranches` is empty when the input only re-covered already-seen branches.
    */
  final case class InputRecord[A](
      index: Int,
      input: A,
      newlyCoveredBranches: Set[Pos]
  )
}
