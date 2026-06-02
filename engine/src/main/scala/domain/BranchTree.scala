package domain

/** Source-level decision graph of one method body. Three node kinds:
  *   - [[Branch]] — a decision point (`if`, `match`, `while`, `for`, partial fn). `kind` is a free-form discriminator so a new construct needs no
  *     change here or in the renderer.
  *   - [[Sequence]] — sibling subtrees under one parent (branchy statements in a block).
  *   - [[Leaf]] — a terminal (non-branchy) expression. **Leaves are what "a branch" means for coverage**: each is one distinct path through the body.
  *     `pos`..`end` is its source span.
  */
sealed trait BranchTree

object BranchTree {

  final case class Branch(kind: String, label: String, arms: List[Arm]) extends BranchTree

  final case class Sequence(children: List[BranchTree]) extends BranchTree

  final case class Leaf(pos: Pos, end: Pos, line: Int, text: String) extends BranchTree {

    /** True if a fired statement at `offset` lies in this leaf's span. End is exclusive so adjacent siblings don't clash; a zero-width span (an
      * implicit `else ()`) is treated as one char wide.
      */
    def spanContains(offset: Pos): Boolean = offset >= pos && offset < math.max(end, pos + 1)
  }

  final case class Arm(label: String, body: BranchTree)

  /** Leaves in document order, but only if the tree has a decision point. A pure-`Leaf` tree (non-branchy method) returns `Nil` so it reports `0/0`
    * and stays out of the comparison.
    */
  def leaves(tree: BranchTree): List[Leaf] =
    if (hasBranch(tree)) collectLeaves(tree) else Nil

  def hasBranch(tree: BranchTree): Boolean = tree match {
    case _: Branch          => true
    case Sequence(children) => children.exists(hasBranch)
    case _: Leaf            => false
  }

  private def collectLeaves(tree: BranchTree): List[Leaf] = tree match {
    case l: Leaf            => List(l)
    case Sequence(children) => children.flatMap(collectLeaves)
    case Branch(_, _, arms) => arms.flatMap(a => collectLeaves(a.body))
  }
}
