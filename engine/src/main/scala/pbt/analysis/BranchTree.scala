package pbt.analysis

/** The decision graph of one method body:
  *   - [[Branch]] — a decision point (`if`, `match`, `while`, `for`, partial function); `kind` is a free-form label so a new construct needs no
  *     change here or in the renderer.
  *   - [[Sequence]] — sibling branchy statements of one block.
  *   - [[Leaf]] — a terminal arm. **A leaf is what "a branch" means for coverage**: one distinct path through the body, identified by its source
  *     span.
  *
  * Each [[Arm]] carries the numeric [[Predicate.Cond]] its guard implies, when expressible — the gradient's branch distance. (Literals for the pool
  * are not held per-arm; they are mined in bulk on the whole method — see [[ParsedMethod]].)
  */
sealed trait BranchTree

object BranchTree {

  final case class Branch(kind: String, label: String, arms: List[Arm]) extends BranchTree
  final case class Sequence(children: List[BranchTree])                 extends BranchTree
  final case class Leaf(pos: Pos, end: Pos, line: Int, text: String)    extends BranchTree {

    /** A fired statement at `offset` lies in this leaf. End is exclusive so adjacent siblings don't clash; a zero-width span is one char wide. */
    def contains(offset: Pos): Boolean = offset >= pos && offset < math.max(end, pos + 1)
  }

  final case class Arm(label: String, guard: Option[Predicate.Cond], body: BranchTree)

  /** Leaves in document order, but only if the tree has a decision point — a pure-`Leaf` (non-branchy) method reports `0/0` and stays out of the
    * comparison.
    */
  def leaves(tree: BranchTree): List[Leaf] = if (hasBranch(tree)) collectLeaves(tree) else Nil

  /** Per-leaf path predicate: the conjunction of guards that must hold to reach it. Only leaves whose *entire* path is numerically expressible are
    * kept (the gradient needs the whole path); the rest get no gradient.
    */
  def leafPaths(tree: BranchTree): Map[Pos, List[Predicate.Cond]] = {
    def go(t: BranchTree, path: List[Predicate.Cond], expressible: Boolean): Map[Pos, List[Predicate.Cond]] = t match {
      case l: Leaf            => if (expressible) Map(l.pos -> path) else Map.empty
      case Sequence(children) => children.flatMap(go(_, path, expressible)).toMap
      case Branch(_, _, arms) => arms.flatMap(a => go(a.body, path ++ a.guard.toList, expressible && a.guard.isDefined)).toMap
    }
    go(tree, Nil, expressible = true)
  }

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
