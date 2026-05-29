package object domain {

  /** A source position as a character offset from the start of a file — the unit at which Scalameta and scoverage agree, so the cheapest correct way
    * to match an AST node to its coverage info.
    */
  type Pos = Int
}
