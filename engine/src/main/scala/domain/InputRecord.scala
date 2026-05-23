package domain

/** What one fuzz-loop iteration produced and observed.
  *
  * `newlyCoveredBranches` are the source-level branch positions this input covered *for the first
  * time* — its incremental contribution to overall coverage. Inputs that only re-cover already-seen
  * branches have an empty set; inputs that hit something new for the first time list those
  * positions here.
  *
  * Parameterised over the input type `A` so the framework works uniformly for `Boolean`, `Int`,
  * `List[Int]`, and any future type with a corresponding ScalaCheck `Gen[A]`.
  */
final case class InputRecord[A](
    index: Int,
    input: A,
    newlyCoveredBranches: Set[Pos]
)
