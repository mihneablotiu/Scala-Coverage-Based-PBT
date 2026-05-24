package adapter.driven.scalameta

import cats.effect.IO
import domain.{BranchTree, MethodTree, Pos}
import port.driven.BranchTreeBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.meta._

/** Builds a [[BranchTree]] for one named method by parsing the source file with Scalameta and
  * deep-walking the method body.
  *
  * `visit` matches known branch constructs (`Term.If`, `Term.Match`, `Term.While`,
  * `Term.PartialFunction`) and emits a `BranchTree.Branch`. For anything else, `descend` recurses
  * into every structural child via Scalameta's `tree.children` — that's what catches branches
  * buried in lambda arguments, `val` RHSes, non-tail block statements, etc.
  *
  * **Adding a new branchy construct** = one new case in `visit`. No domain or renderer changes.
  */
object ScalametaBranchTreeBuilder {

  def apply(): BranchTreeBuilder = new Live

  private final class Live extends BranchTreeBuilder {

    override def build(sourceFile: Path, methodName: String): IO[Option[MethodTree]] = IO {
      val text = Files.readString(sourceFile, StandardCharsets.UTF_8)
      text.parse[Source].toOption.flatMap { src =>
        src
          .collect { case d: Defn.Def if d.name.value == methodName => d }
          .headOption
          .map { defn =>
            MethodTree(
              packageName = enclosingPackage(defn),
              className = enclosingClass(defn),
              methodName = methodName,
              body = visit(defn.body)
            )
          }
      }
    }
  }

  private def ancestors(t: Tree): Iterator[Tree] =
    Iterator.iterate(t.parent)(_.flatMap(_.parent)).takeWhile(_.isDefined).map(_.get)

  private def enclosingPackage(t: Tree): String =
    ancestors(t).collect { case p: Pkg => p.ref.toString }.toList.reverse.mkString(".")

  private def enclosingClass(t: Tree): String =
    ancestors(t)
      .collectFirst {
        case c: Defn.Class  => c.name.value
        case o: Defn.Object => o.name.value
        case tr: Defn.Trait => tr.name.value
      }
      .getOrElse("")

  /** Returns a `Branch` for a recognised construct, a `Sequence` if `tree` contains multiple
    * branchy descendants, or a `Leaf` if it contains none.
    */
  private def visit(tree: Tree): BranchTree = tree match {
    case t: Term.If =>
      BranchTree.Branch(
        posOf(t),
        "if",
        exprOf(t.cond),
        List(BranchTree.Arm("then", visit(t.thenp)), BranchTree.Arm("else", visit(t.elsep)))
      )
    case t: Term.Match =>
      BranchTree.Branch(posOf(t), "match", exprOf(t.expr), t.casesBlock.cases.toList.map(armOf))
    case t: Term.While =>
      BranchTree.Branch(
        posOf(t),
        "while",
        exprOf(t.expr),
        List(BranchTree.Arm("body", visit(t.body)))
      )
    case t: Term.PartialFunction =>
      BranchTree.Branch(
        posOf(t),
        "partial",
        BranchTree.Expr(posOf(t), "⟨arg⟩"),
        t.cases.toList.map(armOf)
      )
    case other => descend(other)
  }

  /** Recurse into every structural child of `tree`, collect branchy results, flatten nested
    * sequences, and return the single child / a Sequence / a Leaf as appropriate.
    */
  private def descend(tree: Tree): BranchTree = {
    val branchy = tree.children.iterator
      .map(visit)
      .flatMap {
        case BranchTree.Sequence(_, cs) => cs
        case n                          => List(n)
      }
      .filter(hasBranch)
      .toList
    branchy match {
      case Nil           => BranchTree.Leaf(posOf(tree), textOf(tree))
      case single :: Nil =>
        // Term.Block wraps a sequence of statements (val + if + ...); scoverage tags the block's
        // own position as a branch arm body. Wrap in a Sequence so its position is preserved in
        // the BranchTree and the report writer can label it instead of falling back to "?".
        tree match {
          case _: Term.Block => BranchTree.Sequence(posOf(tree), List(single))
          case _             => single
        }
      case many          => BranchTree.Sequence(posOf(tree), many)
    }
  }

  private def hasBranch(t: BranchTree): Boolean = t match {
    case _: BranchTree.Branch       => true
    case BranchTree.Sequence(_, cs) => cs.exists(hasBranch)
    case _: BranchTree.Leaf         => false
  }

  private def armOf(c: Case): BranchTree.Arm = {
    val patText = textOf(c.pat)
    val label = c.cond.fold(s"case $patText")(g => s"case $patText if ${textOf(g)}")
    BranchTree.Arm(label, visit(c.body))
  }

  private def exprOf(t: Tree): BranchTree.Expr = BranchTree.Expr(posOf(t), textOf(t))
  private def posOf(t: Tree): Pos = Pos(t.pos.start)
  private def textOf(t: Tree): String = t.toString.replaceAll("\\s+", " ").trim.take(80)
}
