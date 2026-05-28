package domain

/** Source-level decision graph of one method body. Built by walking the Scalameta AST and
  * collecting every branching construct it finds, regardless of where the construct appears (a
  * lambda argument, a `val` RHS, a non-tail block statement, etc.).
  *
  * Three node kinds, deliberately few:
  *
  *   - [[Branch]] — a decision point (`if`, `match`, `while`, `partial`, …). The construct kind is
  *     a free-form `String` discriminator, not a separate ADT variant; the renderer draws every
  *     branch the same way. **Decision points are not branches in the coverage sense** — they're
  *     intermediate nodes whose colour just tells the reader "yes, the predicate was evaluated."
  *     Adding a new construct is a single new case in the AST walker — this file and the renderer
  *     don't change.
  *   - [[Sequence]] — multiple sibling subtrees rooted at the same parent. Used when a `Term.Block`
  *     contains several branchy statements, or a non-branchy parent (e.g. a `Term.Apply`) hides
  *     multiple branchy descendants.
  *   - [[Leaf]] — a terminal expression. **This is what "a branch" means for coverage**: every
  *     `Leaf` is one distinct path through the method body, and `covered / total` always reads as
  *     "paths exercised / paths in the method". The `line` is the source line of the leaf, kept
  *     alongside the position so the writer doesn't need a second lookup table.
  */
sealed trait BranchTree {
  def pos: Pos
}

object BranchTree {

  /** A decision point with `arms.size` labeled outcomes. `label` is the human-readable condition
    * text — `"xs.isEmpty"` for an `if`, the scrutinee text for a `match`, etc.
    */
  final case class Branch(
      pos: Pos,
      kind: String,
      label: String,
      arms: List[Arm]
  ) extends BranchTree

  /** Multiple sibling subtrees attached to the same parent. */
  final case class Sequence(pos: Pos, children: List[BranchTree]) extends BranchTree

  /** Terminal expression — one distinct path through the enclosing method. `pos` is the scoverage
    * key (so the writer can colour it), `line` is the source line for the per-branch summary, and
    * `text` is the rendered body for both the diamond label and the human-readable label.
    */
  final case class Leaf(pos: Pos, line: Int, text: String) extends BranchTree

  /** One outcome of a [[Branch]]. `label` is the human-readable edge text ("then", "else",
    * `case Seq(a, b, c)`, "body", "catch IOException", …); `body` is the subtree rendered below
    * that edge.
    */
  final case class Arm(label: String, body: BranchTree)

  /** True iff any descendant leaf's position is in `covered`. Used to colour a branch node — a
    * branch is "reached" iff at least one of its arms was. Uniform across all branch kinds, so
    * partial functions (no condition position) behave the same as ifs.
    */
  def isReached(tree: BranchTree, covered: Set[Pos]): Boolean = tree match {
    case Leaf(pos, _, _)       => covered(pos)
    case Branch(_, _, _, arms) => arms.exists(a => isReached(a.body, covered))
    case Sequence(_, children) => children.exists(c => isReached(c, covered))
  }

  /** Every leaf in document order, **provided the tree has at least one decision point**.
    *
    * A tree of just a `Leaf` is a non-branching method; it has one source-level path but no
    * decision to be exercised, so reporting "1 branch" would be misleading — we return `Nil`, the
    * report shows `0/0`, and the trivial body falls out of the comparison entirely.
    *
    * The source of truth for "what counts as a branch": the writer's per-branch summary lists
    * exactly these, and the coverage delta in the use case is computed by intersecting scoverage's
    * fired positions with `leaves(...).map(_.pos).toSet`.
    */
  def leaves(tree: BranchTree): List[Leaf] =
    if (hasBranch(tree)) collectLeaves(tree) else Nil

  /** True iff the tree contains at least one [[Branch]] node anywhere — i.e. the method has a
    * decision point. Used by [[leaves]] (to suppress the misleading "1 branch" reading of a
    * pure-`Leaf` tree) and by the Scalameta builder (to skip non-branchy structural children when
    * deciding whether to wrap descendants in a `Sequence`).
    */
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
