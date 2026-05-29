package adapter.driven.scalameta

import domain.{BranchTree, ParsedMethod, Pos}
import port.driven.BranchTreeBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.meta._

/** Builds a [[ParsedMethod]] by parsing the source with Scalameta and deep-walking the method body. `visit` matches known branch constructs;
  * `descend` recurses through structural children so branches buried in lambda args, `val` RHSes, etc. still get captured. Leaf positions are derived
  * from the resulting tree right here so the use case never has to walk it itself. Adding a new branchy construct = one new case in `visit`.
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
          .map { defn =>
            val tree             = visit(defn.body)
            val leaves: Set[Pos] = BranchTree.leaves(tree).iterator.map(_.pos).toSet
            ParsedMethod(tree, leaves)
          }
      }
    }
  }

  private def visit(tree: Tree): BranchTree = tree match {
    case t: Term.If =>
      BranchTree.Branch(
        posOf(t),
        "if",
        textOf(t.cond),
        List(BranchTree.Arm("then", visit(t.thenp)), BranchTree.Arm("else", visit(t.elsep)))
      )
    case t: Term.Match =>
      BranchTree.Branch(posOf(t), "match", textOf(t.expr), t.casesBlock.cases.toList.map(armOf))
    case t: Term.While =>
      BranchTree.Branch(
        posOf(t),
        "while",
        textOf(t.expr),
        List(BranchTree.Arm("body", visit(t.body)))
      )
    case t: Term.PartialFunction =>
      BranchTree.Branch(posOf(t), "partial", "⟨arg⟩", t.cases.toList.map(armOf))
    case other => descend(other)
  }

  private def descend(tree: Tree): BranchTree = {
    val branchy = tree.children.iterator
      .map(visit)
      .flatMap {
        case BranchTree.Sequence(_, cs) => cs
        case n                          => List(n)
      }
      .filter(BranchTree.hasBranch)
      .toList
    branchy match {
      case Nil           => BranchTree.Leaf(posOf(tree), lineOf(tree), textOf(tree))
      case single :: Nil => single
      case many          => BranchTree.Sequence(posOf(tree), many)
    }
  }

  private def armOf(c: Case): BranchTree.Arm = {
    val patText = textOf(c.pat)
    val label   = c.cond.fold(s"case $patText")(g => s"case $patText if ${textOf(g)}")
    BranchTree.Arm(label, visit(c.body))
  }

  private def posOf(t: Tree): Pos = t.pos.start
  // Scalameta lines are 0-based; scoverage and editors are 1-based. Offset here so downstream
  // consumers all read the same number you'd see in your editor.
  private def lineOf(t: Tree): Int    = t.pos.startLine + 1
  private def textOf(t: Tree): String = t.toString.replaceAll("\\s+", " ").trim.take(80)
}
