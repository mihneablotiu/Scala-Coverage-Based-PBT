package domain

/** Output of [[port.driven.BranchTreeBuilder]]: the method's branch tree, the literals mined from its body, and its parameter count (so the
  * coverage-guided objective can bind inputs to parameters).
  */
final case class ParsedMethod(branchTree: BranchTree, constantPool: ConstantPool, paramCount: Int)
