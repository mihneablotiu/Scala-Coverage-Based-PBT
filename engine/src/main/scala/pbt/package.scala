package object pbt {

  /** A source position: a character offset from the start of a file. Scalameta and scoverage share this unit, so it's the cheapest correct way to
    * match an AST node to the coverage it produced.
    */
  type Pos = Int
}
