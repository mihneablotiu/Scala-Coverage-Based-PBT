package adapter.driven.scalameta

import domain.{BranchTree, ConstantPool, ParsedMethod, Pos}
import port.driven.BranchTreeBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.meta._

/** Parses a source file with Scalameta and walks the named method's body into a [[ParsedMethod]]. `visit` matches the branching constructs; `descend`
  * recurses through structural children so branches buried in lambda bodies (folds, `map`, `filter`), `val` RHSes, etc. are still found.
  *
  * Two deliberate rules:
  *   - Nested `def`/`object`/`class` are opaque — they are separate methods (scoverage attributes their statements elsewhere), so we do not expand
  *     them; only the *call* to them shows up, as a leaf in the enclosing body.
  *   - A leaf records its full source span (`pos`..`end`), and the use case marks it covered by span containment, so the tree need not agree with
  *     scoverage on exact statement offsets.
  *
  * Adding a branching construct = one case in `visit`; a mined literal kind = one case in `mineLiterals`.
  */
object ScalametaBranchTreeBuilder {

  def apply(): BranchTreeBuilder = new Live

  private final class Live extends BranchTreeBuilder {
    override def build(sourceFile: Path, methodName: String): Option[ParsedMethod] = {
      val text = Files.readString(sourceFile, StandardCharsets.UTF_8)
      text.parse[Source].toOption.flatMap { src =>
        src
          .collect { case d: Defn.Def if d.name.value == methodName => d }
          .headOption
          .map(defn => ParsedMethod(visit(defn.body), mineLiterals(defn.body)))
      }
    }
  }

  /** Only the kinds a [[ConstantPool]] consumer injects (`ints`, `longs`, `doubles`, `strings`). */
  private def mineLiterals(body: Tree): ConstantPool =
    body.collect { case l: Lit => l }.foldLeft(ConstantPool.empty) {
      case (p, l: Lit.Int)    => p.copy(ints = p.ints + l.value)
      case (p, l: Lit.Long)   => p.copy(longs = p.longs + l.value)
      case (p, l: Lit.Double) => p.copy(doubles = p.doubles + l.value.asInstanceOf[Double]) // Lit.Double.value is typed Any in 4.17
      case (p, l: Lit.String) => p.copy(strings = p.strings + l.value)
      case (p, _)             => p
    }

  private def visit(tree: Tree): BranchTree = tree match {
    case t: Term.If =>
      BranchTree.Branch("if", textOf(t.cond), BranchTree.Arm("then", visit(t.thenp)) :: elseArm(t.elsep))
    case t: Term.Match =>
      BranchTree.Branch("match", textOf(t.expr), t.casesBlock.cases.toList.map(armOf))
    case t: Term.While =>
      BranchTree.Branch("while", textOf(t.expr), List(BranchTree.Arm("body", visit(t.body))))
    case t: Term.ForYield        => forBranch(t, "for-yield", "yield", t.enumsBlock.enums, t.body)
    case t: Term.For             => forBranch(t, "for", "do", t.enumsBlock.enums, t.body)
    case t: Term.PartialFunction =>
      BranchTree.Branch("partial", "⟨arg⟩", t.cases.toList.map(armOf))
    // Nested methods/types are their own scope — do not expand; the call site stays a leaf.
    case _: Defn.Def | _: Defn.Object | _: Defn.Class | _: Defn.Trait =>
      BranchTree.Sequence(Nil)
    case other => descend(other)
  }

  private def descend(tree: Tree): BranchTree = {
    val branchy = tree.children.iterator
      .map(visit)
      .flatMap {
        case BranchTree.Sequence(cs) => cs
        case n                       => List(n)
      }
      .filter(BranchTree.hasBranch)
      .toList
    branchy match {
      case Nil           => BranchTree.Leaf(posOf(tree), endOf(tree), lineOf(tree), textOf(tree))
      case single :: Nil => single
      case many          => BranchTree.Sequence(many)
    }
  }

  /** A for-comprehension desugars to `withFilter`/`map` calls that scoverage records as a single statement spanning the whole expression. So a
    * trivial body becomes one leaf over that whole span; a body with its own `if`/`match` keeps those (they map to their own statements).
    */
  private def forBranch(t: Tree, kind: String, armLabel: String, enums: List[Enumerator], body: Term): BranchTree = {
    val arm = visit(body) match {
      case _: BranchTree.Leaf => BranchTree.Leaf(posOf(t), endOf(t), lineOf(body), textOf(body))
      case branchy            => branchy
    }
    BranchTree.Branch(kind, enumsOf(enums), List(BranchTree.Arm(armLabel, arm)))
  }

  /** An `if` without `else` has a synthetic, zero-width `else` — no meaningful coverage path, so drop it. */
  private def elseArm(elsep: Term): List[BranchTree.Arm] =
    if (elsep.pos.start == elsep.pos.end) Nil
    else List(BranchTree.Arm("else", visit(elsep)))

  private def armOf(c: Case): BranchTree.Arm = {
    val patText = textOf(c.pat)
    val label   = c.cond.fold(s"case $patText")(g => s"case $patText if ${textOf(g)}")
    BranchTree.Arm(label, visit(c.body))
  }

  private def enumsOf(enums: List[Enumerator]): String = clip(enums.map(_.toString).mkString("; "))

  private def posOf(t: Tree): Pos = t.pos.start
  private def endOf(t: Tree): Pos = t.pos.end
  // Scalameta lines are 0-based; scoverage and editors are 1-based.
  private def lineOf(t: Tree): Int    = t.pos.startLine + 1
  private def textOf(t: Tree): String = clip(t.toString)
  private def clip(s: String): String = s.replaceAll("\\s+", " ").trim.take(80)
}
