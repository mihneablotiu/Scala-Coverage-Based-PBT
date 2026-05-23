package domain

/** Per-source-branch outcome for one fuzz session — the unit the writer enumerates in summary,
  * JSON, and CSV.
  *
  *   - `label` — a human-readable description of *what the arm actually is*, sourced from the
  *     [[BranchTree]]: the leaf body text for a terminal arm (`"unsorted"`, `"zero"`) or the
  *     construct + condition for a sub-decision (`"if (xs == xs.sorted)"`). Without it the
  *     `(pos, line)` pair only tells the reader *where* the branch is, not what it means.
  *   - `firstHitInput` — `None` when the branch was never covered.
  */
final case class BranchOutcome(
    pos: Pos,
    line: Int,
    label: String,
    firstHitInput: Option[Int]
)
