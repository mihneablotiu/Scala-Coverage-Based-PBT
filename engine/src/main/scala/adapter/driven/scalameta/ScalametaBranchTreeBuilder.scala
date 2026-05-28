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
      .filter(BranchTree.hasBranch)
      .toList
    branchy match {
      case Nil           => BranchTree.Leaf(posOf(tree), lineOf(tree), textOf(tree))
      case single :: Nil =>
        // Term.Block wraps a sequence of statements (val + if + ...); scoverage tags the block's
        // own position as the position of the branch arm. Wrap in a Sequence so the position is
        // preserved in the BranchTree even though only one structural child is branchy.
        tree match {
          case _: Term.Block => BranchTree.Sequence(posOf(tree), List(single))
          case _             => single
        }
      case many => BranchTree.Sequence(posOf(tree), many)
    }
  }

  private def armOf(c: Case): BranchTree.Arm = {
    val patText = textOf(c.pat)
    val label = c.cond.fold(s"case $patText")(g => s"case $patText if ${textOf(g)}")
    BranchTree.Arm(label, visit(c.body))
  }

  private def posOf(t: Tree): Pos = t.pos.start
  // Scalameta lines are 0-based; scoverage and IDE editors are 1-based. Offsetting here means every
  // downstream consumer ("line 42  trivial …" in the summary, the `line` field in JSON / CSV) reads
  // the same number you'd see in your editor.
  private def lineOf(t: Tree): Int = t.pos.startLine + 1
  private def textOf(t: Tree): String = t.toString.replaceAll("\\s+", " ").trim.take(80)
}
