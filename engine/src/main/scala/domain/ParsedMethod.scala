package domain

/** Output of [[port.driven.BranchTreeBuilder]]: the method's branch tree plus the literals mined from its body.
  */
final case class ParsedMethod(branchTree: BranchTree, constantPool: ConstantPool)
