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
  * The walker has two modes:
  *
  *   - `visit` matches known branch constructs (`Term.If`, `Term.Match`, `Term.While`,
  *     `Term.PartialFunction`, …) and produces a `BranchTree.Branch`.
  *   - For anything else, `descend` recurses into every structural child via Scalameta's
  *     `tree.children`. That's what makes the walker find branches buried inside lambda arguments,
  *     `val` RHSes, non-tail block statements, infix calls, and so on — the cases the old shallow
  *     walker missed.
  *
  * **Adding a new branchy construct** (e.g. `Term.Try`, `Term.ForYield` with filters) is a single
  * new case in `visit` plus one new `branchOf*` method. No domain or renderer changes — both treat
  * every branch uniformly via [[BranchTree.Branch]]'s `kind` string.
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

  // ── Package / class context ────────────────────────────────────────

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

  // ── The walker ─────────────────────────────────────────────────────

  /** Produce a [[BranchTree]] for `tree`. Returns a `Branch` if `tree` is a recognised branch
    * construct, a `Sequence` if it contains multiple branchy descendants, or a `Leaf` if it has
    * none. Delegates the "look deep, not shallow" work to [[descend]].
    */
  private def visit(tree: Tree): BranchTree = tree match {
    case t: Term.If              => ifBranch(t)
    case t: Term.Match           => matchBranch(t)
    case t: Term.While           => whileBranch(t)
    case t: Term.PartialFunction => partialBranch(t)
    case other                   => descend(other)
  }

  /** Recurse into every structural child of `tree`, collect what visits produce, drop the trees
    * that have no branches inside them, and return:
    *
    *   - the single child if there's exactly one branchy result,
    *   - a [[BranchTree.Sequence]] if there are several,
    *   - a [[BranchTree.Leaf]] for `tree` itself if there are none.
    *
    * Nested `Sequence`s are flattened so a Sequence never directly contains another Sequence — that
    * keeps the renderer's job trivial.
    */
  private def descend(tree: Tree): BranchTree = {
    val visited = tree.children.iterator.map(visit).toList
    val flattened = visited.flatMap {
      case BranchTree.Sequence(_, cs) => cs
      case n                          => List(n)
    }
    flattened.filter(hasBranch) match {
      case Nil          => BranchTree.Leaf(posOf(tree), textOf(tree))
      case single :: Nil => single
      case many          => BranchTree.Sequence(posOf(tree), many)
    }
  }

  private def hasBranch(t: BranchTree): Boolean = t match {
    case _: BranchTree.Branch       => true
    case BranchTree.Sequence(_, cs) => cs.exists(hasBranch)
    case _: BranchTree.Leaf         => false
  }

  // ── Branch constructors (one per recognised construct) ─────────────

  private def ifBranch(t: Term.If): BranchTree.Branch =
    BranchTree.Branch(
      pos = posOf(t),
      kind = "if",
      label = exprOf(t.cond),
      arms = List(
        BranchTree.Arm("then", visit(t.thenp)),
        BranchTree.Arm("else", visit(t.elsep))
      )
    )

  private def matchBranch(t: Term.Match): BranchTree.Branch =
    BranchTree.Branch(
      pos = posOf(t),
      kind = "match",
      label = exprOf(t.expr),
      arms = t.casesBlock.cases.toList.map(armOf)
    )

  private def whileBranch(t: Term.While): BranchTree.Branch =
    BranchTree.Branch(
      pos = posOf(t),
      kind = "while",
      label = exprOf(t.expr),
      arms = List(BranchTree.Arm("body", visit(t.body)))
    )

  private def partialBranch(t: Term.PartialFunction): BranchTree.Branch =
    BranchTree.Branch(
      pos = posOf(t),
      kind = "partial",
      label = BranchTree.Expr(posOf(t), "⟨arg⟩"),
      arms = t.cases.toList.map(armOf)
    )

  private def armOf(c: Case): BranchTree.Arm =
    BranchTree.Arm(s"case ${textOf(c.pat)}", visit(c.body))

  // ── Helpers ────────────────────────────────────────────────────────

  private def exprOf(t: Tree): BranchTree.Expr = BranchTree.Expr(posOf(t), textOf(t))

  private def posOf(t: Tree): Pos = Pos(t.pos.start)

  private def textOf(t: Tree): String = t.toString.replaceAll("\\s+", " ").trim.take(80)
}
