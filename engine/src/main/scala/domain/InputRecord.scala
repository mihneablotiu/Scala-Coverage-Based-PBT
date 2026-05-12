package domain

/** What one fuzz-loop iteration produced and observed. */
final case class InputRecord(
    index: Int,
    input: Int,
    linesExercised: Set[Int]
)
