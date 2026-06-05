package pbt.analysis

import pbt.gen.ConstantPool

import java.nio.file.{Files, Path}
import scala.meta._

/** The decision graph of one method body:
  *   - [[BranchTree.Branch]] — a decision point (`if`, `match`, `while`, `for`); `kind` is a free-form label, so a new construct needs no change here
  *     or in the report renderer.
  *   - [[BranchTree.Sequence]] — sibling branchy statements in one block.
  *   - [[BranchTree.Leaf]] — a terminal arm, identified by its source span (`start` until `end`). **A leaf is the unit of coverage.**
  */
sealed trait BranchTree {

  def hasBranch: Boolean = this match {
    case _: BranchTree.Branch    => true
    case BranchTree.Sequence(cs) => cs.exists(_.hasBranch)
    case _: BranchTree.Leaf      => false
  }

  /** Leaves in document order — but only if this tree has a decision point. A non-branchy method reports `0/0` and stays out of the comparison. */
  def leaves: List[BranchTree.Leaf] = if (hasBranch) allLeaves else Nil

  private def allLeaves: List[BranchTree.Leaf] = this match {
    case l: BranchTree.Leaf         => List(l)
    case BranchTree.Sequence(cs)    => cs.flatMap(_.allLeaves)
    case BranchTree.Branch(_, _, a) => a.flatMap(_.body.allLeaves)
  }
}

object BranchTree {
  final case class Branch(kind: String, label: String, arms: List[Arm]) extends BranchTree
  final case class Sequence(children: List[BranchTree])                 extends BranchTree
  final case class Arm(label: String, body: BranchTree)

  /** `end` is exclusive so adjacent siblings don't clash; a zero-width span is treated as one char wide. */
  final case class Leaf(start: Int, end: Int, line: Int, text: String) extends BranchTree {
    def contains(offset: Int): Boolean = offset >= start && offset < math.max(end, start + 1)
  }
}

/** A parsed method: its decision graph and the literals mined from its body (for the Pool tactic to inject). */
final case class ParsedMethod(tree: BranchTree, pool: ConstantPool)

/** Parse a source file with Scalameta and walk the named method's body into a [[BranchTree]]:
  *   - `visit` matches the branching constructs; `descend` recurses so branches buried in lambdas / `val` RHSes still surface;
  *   - nested `def` / `object` / `class` are opaque (their own scope) — only the call site shows, as a leaf;
  *   - a leaf stores its full source span, so coverage matches by span containment;
  *   - integer literals are mined in bulk — over-approximating is cheap, an unused literal is just a wasted draw.
  */
object Parser {

  def parse(sourceFile: Path, method: String): Option[ParsedMethod] =
    Files
      .readString(sourceFile)
      .parse[Source]
      .toOption
      .flatMap(_.collect { case d: Defn.Def if d.name.value == method => d }.headOption)
      .map(d => ParsedMethod(visit(d.body), mineLiterals(d.body)))

  private def visit(tree: Tree): BranchTree = tree match {
    case t: Term.If =>
      val thenArm = BranchTree.Arm("then", visit(t.thenp))
      // An `if` without `else` has a synthetic, zero-width `else` — no real path, so drop it.
      val elseArm = if (t.elsep.pos.start == t.elsep.pos.end) Nil else List(BranchTree.Arm("else", visit(t.elsep)))
      BranchTree.Branch("if", text(t.cond), thenArm :: elseArm)
    case t: Term.Match           => BranchTree.Branch("match", text(t.expr), t.casesBlock.cases.toList.map(arm))
    case t: Term.While           => BranchTree.Branch("while", text(t.expr), List(BranchTree.Arm("body", visit(t.body))))
    case t: Term.PartialFunction => BranchTree.Branch("partial", "⟨arg⟩", t.cases.toList.map(arm))
    case _: Defn.Def | _: Defn.Object | _: Defn.Class | _: Defn.Trait => BranchTree.Sequence(Nil) // opaque nested scope
    case other                                                        => descend(other)
  }

  private def descend(tree: Tree): BranchTree = {
    val branches = tree.children
      .map(visit)
      .flatMap {
        case BranchTree.Sequence(cs) => cs
        case n                       => List(n)
      }
      .filter(_.hasBranch)
    branches match {
      case Nil           => leaf(tree)
      case single :: Nil => single
      case many          => BranchTree.Sequence(many)
    }
  }

  private def arm(c: Case): BranchTree.Arm = {
    val label = c.cond.fold(s"case ${text(c.pat)}")(g => s"case ${text(c.pat)} if ${text(g)}")
    BranchTree.Arm(label, visit(c.body))
  }

  private def mineLiterals(t: Tree): ConstantPool =
    t.collect { case l: Lit => l }.foldLeft(ConstantPool.empty) {
      case (p, l: Lit.Int) => p.copy(ints = p.ints + l.value)
      case (p, _)          => p
    }

  private def leaf(t: Tree): BranchTree.Leaf = BranchTree.Leaf(t.pos.start, t.pos.end, t.pos.startLine + 1, text(t))
  private def text(t: Tree): String          = t.toString.replaceAll("\\s+", " ").trim.take(80)
}
