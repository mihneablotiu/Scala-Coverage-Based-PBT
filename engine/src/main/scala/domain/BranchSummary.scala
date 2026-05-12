package domain

/** Aggregate per-line coverage info accumulated across all inputs of a session.
  *
  * `counter` = the cumulative covered/total at this line (from JaCoCo). `hitCount` = number of
  * inputs whose execution touched this line. `firstHitInputIndex` = index of the first input that
  * touched this line.
  */
final case class BranchSummary(
    counter: BranchCounter,
    hitCount: Int,
    firstHitInputIndex: Option[Int]
)
