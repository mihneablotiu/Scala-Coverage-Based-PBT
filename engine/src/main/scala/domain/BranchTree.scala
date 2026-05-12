package domain

/** AST of branchy expressions in one method, as parsed by a [[port.driven.BranchTreeBuilder]].
  *
  * Every node carries its own source [[Pos]]. The writer matches these against the set of executed
  * positions reported by [[port.driven.SourceCoverageReader]] to colour each node accurately —
  * green for executed, red for not.
  *
  * Adding a new construct (Try, For, …) is a single new variant here plus one case in the Scalameta
  * builder and one in the writer's recursive walk.
  */
sealed trait BranchTree {
  def pos: Pos
}

object BranchTree {

  final case class If(
      pos: Pos,
      condition: Expr,
      thenBranch: BranchTree,
      elseBranch: BranchTree
  ) extends BranchTree

  final case class Match(
      pos: Pos,
      scrutinee: Expr,
      cases: List[CaseArm]
  ) extends BranchTree

  final case class While(
      pos: Pos,
      condition: Expr,
      body: BranchTree
  ) extends BranchTree

  final case class Leaf(pos: Pos, text: String) extends BranchTree

  /** A sub-expression with its own position — the condition of an `if`/`while`, the scrutinee of a
    * `match`, or the pattern of a case arm. Tracked so the writer can colour the condition node
    * based on whether the condition itself was reached.
    */
  final case class Expr(pos: Pos, text: String)

  final case class CaseArm(pos: Pos, pattern: Expr, body: BranchTree)
}
