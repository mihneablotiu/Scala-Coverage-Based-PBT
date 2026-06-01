package domain

/** Read-only view of one fuzz session so far.
  *
  * Three fields, all monotonically grown by [[append]]:
  *   - `history` — one entry per input: the set of branches *first* covered by that input (its delta). Drives the growth curve and per-leaf
  *     first-hit; the input value itself is never needed here, only in `seeds`.
  *   - `seeds` — the input values whose iteration newly covered a branch (the corpus consumed by the mutation strategies).
  *   - `coveredBranches` — the cumulative set, kept as a field (not re-derived) so `append` stays O(delta) instead of O(history) per call.
  *
  * Construct only via `empty` + `append`; the three fields must stay consistent.
  */
final case class SessionFeedback[A](
    history: Vector[Set[Pos]],
    seeds: Vector[A],
    coveredBranches: Set[Pos]
) {

  def iteration: Int = history.size

  /** Cumulative covered-leaf count per iteration. Computed once, by the writer. */
  def growthCurve: Vector[Int] = history.scanLeft(0)(_ + _.size).tail

  /** `nowCovered` is the cumulative set of leaf positions fired so far (the use case intersects scoverage's hits with the method's leaves before
    * calling). The stored delta is what's new versus `coveredBranches`; `seeds` grows iff that delta is non-empty.
    */
  def append(input: A, nowCovered: Set[Pos]): SessionFeedback[A] = {
    val delta = nowCovered -- coveredBranches
    SessionFeedback(
      history :+ delta,
      if (delta.nonEmpty) seeds :+ input else seeds,
      coveredBranches ++ delta
    )
  }
}

object SessionFeedback {
  def empty[A]: SessionFeedback[A] = SessionFeedback(Vector.empty, Vector.empty, Set.empty)
}
