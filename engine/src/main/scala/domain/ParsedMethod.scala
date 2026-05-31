package domain

/** Output of the [[port.driven.BranchTreeBuilder]] port: branch tree, leaf positions, and the literal dictionary mined from the method body. The leaf
  * set + pool are computed once at parse time so the use case stays free of any tree- or AST-walking logic.
  */
final case class ParsedMethod(
    branchTree: BranchTree,
    leafPositions: Set[Pos],
    constantPool: ConstantPool
)
