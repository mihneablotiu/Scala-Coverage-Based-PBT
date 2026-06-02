package domain

/** Read-only view of one fuzz session so far, grown only by [[append]]:
  *   - `history` — per input, the set of leaves it *first* covered (its delta); drives the growth curve and per-leaf first-hit.
  *   - `seeds` — the inputs whose iteration newly covered a leaf (the corpus the mutation strategies perturb).
  *   - `coveredBranches` — the cumulative set, kept as a field so `append` is O(delta), not O(history).
  *   - `lastGainAt` — the iteration count after the most recent coverage-increasing input, so `stall` is O(1).
  */
final case class SessionFeedback[A](
    history: Vector[Set[Pos]],
    seeds: Vector[A],
    coveredBranches: Set[Pos],
    lastGainAt: Int
) {

  def iteration: Int = history.size

  /** Inputs run since the last one that covered a new leaf (0 right after a discovery). */
  def stall: Int = history.size - lastGainAt

  /** Cumulative covered-leaf count per iteration. */
  def growthCurve: Vector[Int] = history.scanLeft(0)(_ + _.size).tail

  /** `nowCovered` is the cumulative set of covered leaves; the stored delta is what's new versus `coveredBranches`, and `seeds` grows iff that delta
    * is non-empty.
    */
  def append(input: A, nowCovered: Set[Pos]): SessionFeedback[A] = {
    val delta  = nowCovered -- coveredBranches
    val gained = delta.nonEmpty
    SessionFeedback(
      history :+ delta,
      if (gained) seeds :+ input else seeds,
      coveredBranches ++ delta,
      if (gained) history.size + 1 else lastGainAt
    )
  }
}

object SessionFeedback {
  def empty[A]: SessionFeedback[A] = SessionFeedback(Vector.empty, Vector.empty, Set.empty, 0)
}
