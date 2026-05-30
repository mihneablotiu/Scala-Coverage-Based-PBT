package domain

/** Read-only view of one fuzz session so far.
  *
  * Two stored fields, both monotonically grown by [[append]]:
  *   - `history` — every input ever evaluated, with its newly-covered-branch delta;
  *   - `seeds`   — the subset of inputs whose iteration newly covered a branch (the corpus consumed by
  *                 `Strategy.MutationGuided`). Cached here so `gen` is O(1) per call instead of re-scanning
  *                 the whole `history` each iteration.
  *
  * `coveredBranches` and `growthCurve` are derived (`lazy val` because both the writer and `append`'s
  * delta computation touch them). The two stored fields must stay consistent — go through `empty` +
  * `append`, never construct a `SessionFeedback` directly with a `seeds` that disagrees with `history`.
  */
final case class SessionFeedback[A](
    history: Vector[SessionFeedback.InputRecord[A]],
    seeds: Vector[A]
) {

  def iteration: Int = history.size

  lazy val coveredBranches: Set[Pos] =
    history.iterator.flatMap(_.newlyCoveredBranches).toSet

  lazy val growthCurve: Vector[Int] =
    history.scanLeft(0)((c, r) => c + r.newlyCoveredBranches.size).tail

  /** `nowCovered` is the cumulative set of leaf positions fired so far (the use case intersects scoverage's hits with the method's leaves before
    * calling). The stored record keeps only the delta against the running `coveredBranches`; `seeds` grows iff that delta is non-empty.
    */
  def append(input: A, nowCovered: Set[Pos]): SessionFeedback[A] = {
    val delta    = nowCovered -- coveredBranches
    val record   = SessionFeedback.InputRecord(iteration, input, delta)
    val newSeeds = if (delta.nonEmpty) seeds :+ input else seeds
    SessionFeedback(history :+ record, newSeeds)
  }
}

object SessionFeedback {

  def empty[A]: SessionFeedback[A] = SessionFeedback(Vector.empty, Vector.empty)

  /** One iteration's record. `newlyCoveredBranches` is empty when the input only re-covered already-seen branches.
    */
  final case class InputRecord[A](
      index: Int,
      input: A,
      newlyCoveredBranches: Set[Pos]
  )
}
