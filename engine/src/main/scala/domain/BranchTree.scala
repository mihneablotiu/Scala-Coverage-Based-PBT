package domain

/** Source-level decision graph of one method body. Built by walking the Scalameta AST and
  * collecting every branching construct it finds, regardless of where the construct appears (a
  * lambda argument, a `val` RHS, a non-tail block statement, etc.).
  *
  * Three node kinds, deliberately few:
  *
  *   - [[Branch]] — a decision point. The construct kind ("if", "match", "while", "partial", …) is
  *     a free-form `String` discriminator, not a separate ADT variant. The renderer draws every
  *     branch the same way: a box with `kind` + `label` and one outgoing edge per arm. **Adding a
  *     new construct is a single new case in the AST walker — this file and the renderer don't
  *     change.**
  *   - [[Sequence]] — multiple sibling subtrees rooted at the same parent. Used when a `Term.Block`
  *     contains several branchy statements, or a non-branchy parent (e.g. a `Term.Apply`) hides
  *     multiple branchy descendants.
  *   - [[Leaf]] — a terminal expression. Coloured by whether its position was invoked.
  *
  * Coverage is overlaid by the writer at render time using a `Set[Pos]` of invoked positions; see
  * [[isReached]].
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

  /** Terminal expression — its position determines its colour in the picture. */
  final case class Leaf(pos: Pos, text: String) extends BranchTree

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
    case Leaf(pos, _)          => covered(pos)
    case Branch(_, _, _, arms) => arms.exists(a => isReached(a.body, covered))
    case Sequence(_, children) => children.exists(c => isReached(c, covered))
  }

  /** Walks the tree and records a human-readable description for every node's position. Used by the
    * report writer to label each scoverage branch position with what it actually represents.
    *
    *   - Each `Leaf(pos, text)` contributes `pos -> text` — the terminal arm's body text (e.g.
    *     `"unsorted"`, `"zero"`).
    *   - Each `Branch(pos, kind, label, _)` contributes `pos -> s"$kind ($label)"` — the construct
    *     + condition for a sub-decision (e.g. `"if (xs == xs.sorted)"`). This is what a nested
    *     `if`'s position scoverage reports as a "branch arm fired" actually means.
    */
  def collectLabels(tree: BranchTree): Map[Pos, String] = tree match {
    case Leaf(pos, text) =>
      Map(pos -> text)
    case Sequence(pos, children) =>
      val self = Map(pos -> "<block>")
      children.foldLeft(self)((acc, c) => acc ++ collectLabels(c))
    case Branch(pos, kind, label, arms) =>
      val self = Map(pos -> s"$kind ($label)")
      arms.foldLeft(self)((acc, arm) => acc ++ collectLabels(arm.body))
  }
}
