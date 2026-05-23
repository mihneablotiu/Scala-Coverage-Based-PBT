package domain

/** Per-source-branch outcome for one fuzz session — the unit the writer enumerates in summary,
  * JSON, and CSV. `firstHitInput` is `None` when the branch was never covered.
  */
final case class BranchOutcome(
    pos: Pos,
    line: Int,
    firstHitInput: Option[Int]
)
