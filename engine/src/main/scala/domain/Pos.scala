package domain

/** A source position, expressed as a *character offset* from the start of the source file. This is
  * the granularity at which Scalameta and scoverage naturally agree, so it's the cheapest correct
  * way to match an AST node to its coverage info.
  */
final case class Pos(offset: Int)
