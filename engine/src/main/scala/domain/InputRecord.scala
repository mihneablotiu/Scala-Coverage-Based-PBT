package domain

/** What one fuzz-loop iteration produced and observed.
  *
  * Parameterised over the input type `A` so the framework works uniformly for `Boolean`, `Int`,
  * `List[Int]`, and any future type with a corresponding ScalaCheck `Gen[A]`.
  */
final case class InputRecord[A](
    index: Int,
    input: A,
    linesExercised: Set[Int]
)
