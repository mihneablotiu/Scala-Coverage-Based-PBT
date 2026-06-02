package domain

/** Read-only view of one fuzz session so far, grown only by [[append]]:
  *   - `history` — per input, the set of leaves it *first* covered (its delta); drives the growth curve and per-leaf first-hit.
  *   - `seeds` — the inputs whose iteration newly covered a leaf (the corpus the mutation strategies perturb).
  *   - `coveredBranches` — the cumulative set, kept as a field so `append` is O(delta), not O(history).
  */
final case class SessionFeedback[A](
    history: Vector[Set[Pos]],
    seeds: Vector[A],
    coveredBranches: Set[Pos]
) {

  def iteration: Int = history.size

  /** Cumulative covered-leaf count per iteration. */
  def growthCurve: Vector[Int] = history.scanLeft(0)(_ + _.size).tail

  /** `nowCovered` is the cumulative set of covered leaves; the stored delta is what's new versus `coveredBranches`, and `seeds` grows iff that delta
    * is non-empty.
    */
  def append(input: A, nowCovered: Set[Pos]): SessionFeedback[A] = {
    val delta = nowCovered -- coveredBranches
    SessionFeedback(history :+ delta, if (delta.nonEmpty) seeds :+ input else seeds, coveredBranches ++ delta)
  }
}

object SessionFeedback {
  def empty[A]: SessionFeedback[A] = SessionFeedback(Vector.empty, Vector.empty, Set.empty)
}
