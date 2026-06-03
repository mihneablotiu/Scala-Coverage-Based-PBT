package pbt.analysis

import pbt.Pos
import pbt.gen.ConstantPool

/** The decision graph of one method body:
  *   - [[Branch]] — a decision point (`if`, `match`, `while`, `for`, partial function); `kind` is a free-form label so a new construct needs no
  *     change here or in the renderer.
  *   - [[Sequence]] — sibling branchy statements of one block.
  *   - [[Leaf]] — a terminal arm. **A leaf is what "a branch" means for coverage**: one distinct path through the body, identified by its source
  *     span.
  *
  * Each [[Arm]] carries what its guard told us: an optional [[Predicate.Cond]] (the gradient's branch distance) and the literals it mentions (the
  * pool). Only the *satisfying* side of a guard carries literals — taking the `else` of `n == 42` needs `n ≠ 42`, which no literal helps — so they
  * propagate down the arm they actually unlock.
  */
sealed trait BranchTree

object BranchTree {

  final case class Branch(kind: String, label: String, arms: List[Arm]) extends BranchTree
  final case class Sequence(children: List[BranchTree])                 extends BranchTree
  final case class Leaf(pos: Pos, end: Pos, line: Int, text: String)    extends BranchTree {

    /** A fired statement at `offset` lies in this leaf. End is exclusive so adjacent siblings don't clash; a zero-width span is one char wide. */
    def contains(offset: Pos): Boolean = offset >= pos && offset < math.max(end, pos + 1)
  }

  final case class Arm(label: String, guard: Option[Predicate.Cond], literals: ConstantPool, body: BranchTree)

  /** Leaves in document order, but only if the tree has a decision point — a pure-`Leaf` (non-branchy) method reports `0/0` and stays out of the
    * comparison.
    */
  def leaves(tree: BranchTree): List[Leaf] = if (hasBranch(tree)) collectLeaves(tree) else Nil

  /** Per-leaf path predicate: the guards that must hold to reach it. Only leaves whose *entire* path is numerically expressible are included. */
  def leafPaths(tree: BranchTree): Map[Pos, List[Predicate.Cond]] =
    fold(tree, List.empty[Predicate.Cond])(
      leaf = (l, acc, ok) => if (ok) Map(l.pos -> acc) else Map.empty,
      step = (arm, acc) => (acc ++ arm.guard.toList, arm.guard.isDefined)
    )

  /** Per-leaf literals: the pool of every guard on the path that the leaf *satisfies*. The pool tactic injects, for each uncovered leaf, the literals
    * it still needs.
    */
  def leafLiterals(tree: BranchTree): Map[Pos, ConstantPool] =
    fold(tree, ConstantPool.empty)(
      leaf = (l, acc, _) => Map(l.pos -> acc),
      step = (arm, acc) => (acc ++ arm.literals, true)
    )

  def hasBranch(tree: BranchTree): Boolean = tree match {
    case _: Branch          => true
    case Sequence(children) => children.exists(hasBranch)
    case _: Leaf            => false
  }

  /** Shared walk that threads an accumulator down each arm and snapshots it at every leaf. `step` updates the accumulator (and an `ok` flag) per arm;
    * `leaf` builds the per-leaf entry. `ok` lets [[leafPaths]] drop leaves whose path isn't fully expressible.
    */
  private def fold[B](tree: BranchTree, init: B)(
      leaf: (Leaf, B, Boolean) => Map[Pos, B],
      step: (Arm, B) => (B, Boolean)
  ): Map[Pos, B] = {
    def go(t: BranchTree, acc: B, ok: Boolean): Map[Pos, B] = t match {
      case l: Leaf            => leaf(l, acc, ok)
      case Sequence(children) => children.flatMap(go(_, acc, ok)).toMap
      case Branch(_, _, arms) =>
        arms.flatMap { a =>
          val (acc2, ok2) = step(a, acc); go(a.body, acc2, ok && ok2)
        }.toMap
    }
    go(tree, init, ok = true)
  }

  private def collectLeaves(tree: BranchTree): List[Leaf] = tree match {
    case l: Leaf            => List(l)
    case Sequence(children) => children.flatMap(collectLeaves)
    case Branch(_, _, arms) => arms.flatMap(a => collectLeaves(a.body))
  }
}
