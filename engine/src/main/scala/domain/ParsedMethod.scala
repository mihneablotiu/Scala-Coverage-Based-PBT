package domain

/** Output of the [[port.driven.BranchTreeBuilder]] port: the method's branch tree plus the literal dictionary mined from its body. Leaf positions are
  * derived from the tree by the use case via [[BranchTree.leaves]].
  */
final case class ParsedMethod(
    branchTree: BranchTree,
    constantPool: ConstantPool
)
