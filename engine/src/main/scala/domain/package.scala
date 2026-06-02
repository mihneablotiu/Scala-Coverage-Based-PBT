package object domain {

  /** A source position as a character offset from the start of a file — the unit Scalameta and scoverage share, so the cheapest correct way to match
    * an AST node to its coverage.
    */
  type Pos = Int
}
