package domain

/** Output of the [[port.driven.BranchTreeBuilder]] port: a method's branch tree paired with the positions of its leaves (the canonical "branches" for
  * coverage).
  *
  * Bundling them lets the AST adapter compute the leaf set once, at parse time, so the use case stays free of any tree-walking logic.
  */
final case class ParsedMethod(branchTree: BranchTree, leafPositions: Set[Pos])
