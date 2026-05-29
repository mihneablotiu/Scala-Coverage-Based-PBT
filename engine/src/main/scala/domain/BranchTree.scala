package domain

/** Source-level decision graph of one method body. Three node kinds:
  *
  *   - [[Branch]] — a decision point (`if`, `match`, `while`, partial fn). `kind` is a free-form discriminator so adding a new construct doesn't
  *     change this file or the renderer.
  *   - [[Sequence]] — sibling subtrees attached to the same parent (e.g. multiple branchy statements in a `Term.Block`).
  *   - [[Leaf]] — a terminal expression. **Leaves are what "a branch" means for coverage**: each is one distinct path through the method body;
  *     decision points are intermediate nodes.
  */
sealed trait BranchTree {
  def pos: Pos
}

object BranchTree {

  final case class Branch(
      pos: Pos,
      kind: String,
      label: String,
      arms: List[Arm]
  ) extends BranchTree

  final case class Sequence(pos: Pos, children: List[BranchTree]) extends BranchTree

  final case class Leaf(pos: Pos, line: Int, text: String) extends BranchTree

  final case class Arm(label: String, body: BranchTree)

  /** Leaves in document order, **only if the tree has at least one decision point**. A pure-`Leaf` tree (non-branchy method) returns `Nil` so it
    * reports `0/0` and stays out of the comparison.
    */
  def leaves(tree: BranchTree): List[Leaf] =
    if (hasBranch(tree)) collectLeaves(tree) else Nil

  def hasBranch(tree: BranchTree): Boolean = tree match {
    case _: Branch             => true
    case Sequence(_, children) => children.exists(hasBranch)
    case _: Leaf               => false
  }

  private def collectLeaves(tree: BranchTree): List[Leaf] = tree match {
    case l: Leaf               => List(l)
    case Sequence(_, children) => children.flatMap(collectLeaves)
    case Branch(_, _, _, arms) => arms.flatMap(a => collectLeaves(a.body))
  }
}
