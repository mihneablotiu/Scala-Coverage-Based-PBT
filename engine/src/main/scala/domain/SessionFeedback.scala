package domain

/** Read-only view of everything observed so far in a fuzz session, in source-level terms.
  *
  * Holds a single irreducible field — the per-iteration `history` of inputs and their incremental
  * coverage contributions. Everything else (current running set, cumulative growth curve, iteration
  * count) is derived from it.
  *
  * Parameterised over the input type `A`.
  */
final case class SessionFeedback[A](history: Vector[SessionFeedback.InputRecord[A]]) {

  def iteration: Int = history.size

  /** Branch positions covered so far in this session — union of every record's
    * `newlyCoveredBranches`. `lazy val` because the writer and the `append` delta computation both
    * touch it; recomputing is `O(history.size)`.
    */
  lazy val coveredBranches: Set[Pos] =
    history.iterator.flatMap(_.newlyCoveredBranches).toSet

  /** Cumulative covered-branch count after each iteration. Drives `growth.svg` and the saturation
    * index in the writer.
    */
  lazy val growthCurve: Vector[Int] =
    history.scanLeft(0)((c, r) => c + r.newlyCoveredBranches.size).tail

  /** Append a new iteration record. `nowCovered` is the cumulative set of *leaf* positions fired so
    * far (scoverage's hits intersected with the method's leaves by the use case before calling
    * here); the delta against the running `coveredBranches` is what this input contributed.
    */
  def append(input: A, nowCovered: Set[Pos]): SessionFeedback[A] =
    SessionFeedback(
      history :+ SessionFeedback.InputRecord(iteration, input, nowCovered -- coveredBranches)
    )
}

object SessionFeedback {

  def empty[A]: SessionFeedback[A] = SessionFeedback(Vector.empty)

  /** What one fuzz-loop iteration produced and observed. `newlyCoveredBranches` are the source
    * positions this input covered *for the first time* — its incremental contribution. Inputs that
    * only re-cover already-seen branches have an empty set here.
    */
  final case class InputRecord[A](
      index: Int,
      input: A,
      newlyCoveredBranches: Set[Pos]
  )
}
