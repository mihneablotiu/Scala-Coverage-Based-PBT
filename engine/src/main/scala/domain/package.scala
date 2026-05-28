package object domain {

  /** A source position, expressed as a character offset from the start of a source file — the
    * granularity at which Scalameta and scoverage agree, so the cheapest correct way to match an
    * AST node to its coverage info. Kept as a type alias rather than a wrapper class because the
    * value semantics of `Int` are exactly what we want and the alias still documents intent at the
    * field level.
    */
  type Pos = Int
}
