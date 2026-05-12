package adapter.driven.scalameta

import cats.effect.IO
import domain.{BranchTree, MethodTree, Pos}
import port.driven.BranchTreeBuilder

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.meta._

/** Parses a Scala source file with Scalameta and extracts the named method's branchy body as a
  * [[BranchTree]] along with its enclosing package and class. Every node's [[Pos]] is the character
  * offset Scalameta reports, which matches scoverage's statement offsets used by the writer for
  * green/red colouring.
  *
  * Supports `if`, `match`, `while`. Anything else collapses to a [[BranchTree.Leaf]]. Extending to
  * `try`/`for` is a single new case in [[convertTerm]].
  */
object ScalametaBranchTreeBuilder {

  def apply(): BranchTreeBuilder = new Live

  private final class Live extends BranchTreeBuilder {

    override def build(sourceFile: Path, methodName: String): IO[Option[MethodTree]] = IO {
      val text = Files.readString(sourceFile, StandardCharsets.UTF_8)
      text.parse[Source].toOption.flatMap { src =>
        src.collect { case d: Defn.Def if d.name.value == methodName => d }.headOption.map { defn =>
          MethodTree(
            packageName = enclosingPackage(defn),
            className = enclosingClass(defn),
            methodName = methodName,
            body = convertTerm(defn.body)
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

  private def convertTerm(term: Term): BranchTree = term match {
    case t: Term.If =>
      BranchTree.If(posOf(t), exprOf(t.cond), convertTerm(t.thenp), convertTerm(t.elsep))
    case t: Term.Match =>
      BranchTree.Match(posOf(t), exprOf(t.expr), t.casesBlock.cases.map(convertCase).toList)
    case t: Term.While => BranchTree.While(posOf(t), exprOf(t.expr), convertTerm(t.body))
    case b: Term.Block =>
      // A method body wrapped in `{ … }` — recurse into the last statement.
      b.stats.lastOption match {
        case Some(t: Term) => convertTerm(t)
        case _             => BranchTree.Leaf(posOf(b), textOf(b))
      }
    case other => BranchTree.Leaf(posOf(other), textOf(other))
  }

  private def convertCase(c: Case): BranchTree.CaseArm =
    BranchTree.CaseArm(posOf(c), exprOf(c.pat), convertTerm(c.body))

  private def exprOf(t: Tree): BranchTree.Expr = BranchTree.Expr(posOf(t), textOf(t))

  private def posOf(t: Tree): Pos = Pos(t.pos.start)

  private def textOf(t: Tree): String = t.toString.replaceAll("\\s+", " ").trim.take(80)
}
