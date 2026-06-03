package pbt.analysis

import pbt.Pos
import pbt.gen.ConstantPool

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.meta._

/** The method's decision graph plus its parameter count (so the gradient can bind inputs to parameters). */
final case class ParsedMethod(tree: BranchTree, paramCount: Int)

/** Parses a source file with Scalameta and walks the named method's body into a [[BranchTree]]. `visit` matches the branching constructs; `descend`
  * recurses so branches buried in lambdas/folds and `val` RHSes still surface.
  *
  * Each arm records what its guard told us: a numeric [[Predicate.Cond]] when expressible (for the gradient) and the literals it mentions (for the
  * pool). Two deliberate rules keep the graph honest: nested `def`/`object`/`class` are opaque (their own scope — only the call shows), and a leaf
  * stores its full source span so coverage matches by span containment, not exact offsets.
  */
object Parser {

  private type Env = Map[String, Predicate.Expr]

  def parse(sourceFile: Path, method: String): Option[ParsedMethod] = {
    val text = Files.readString(sourceFile, StandardCharsets.UTF_8)
    text.parse[Source].toOption.flatMap { src =>
      src.collect { case d: Defn.Def if d.name.value == method => d }.headOption.map { defn =>
        val params = defn.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).map(_.name).collect { case n: Term.Name => n.value }
        val env    = params.zipWithIndex.map { case (name, i) => name -> (Predicate.Par(i): Predicate.Expr) }.toMap
        ParsedMethod(visit(defn.body, env), params.size)
      }
    }
  }

  private def visit(tree: Tree, env: Env): BranchTree = tree match {
    case t: Term.If =>
      val cond  = condOf(t.cond, env)
      val then0 = BranchTree.Arm("then", cond, litsOf(t.cond), visit(t.thenp, env))
      // An `if` without `else` has a synthetic, zero-width `else` — no meaningful path, so drop it. The else satisfies ¬cond, which no literal helps.
      val elses =
        if (t.elsep.pos.start == t.elsep.pos.end) Nil
        else List(BranchTree.Arm("else", cond.map(Predicate.Not), ConstantPool.empty, visit(t.elsep, env)))
      BranchTree.Branch("if", textOf(t.cond), then0 :: elses)
    case t: Term.Match =>
      val scrut = exprOf(t.expr, env)
      BranchTree.Branch("match", textOf(t.expr), t.casesBlock.cases.toList.map(armOf(_, scrut, env)))
    case t: Term.While =>
      BranchTree.Branch("while", textOf(t.expr), List(BranchTree.Arm("body", None, ConstantPool.empty, visit(t.body, env))))
    case t: Term.ForYield        => forBranch(t, "for-yield", "yield", t.enumsBlock.enums, t.body, env)
    case t: Term.For             => forBranch(t, "for", "do", t.enumsBlock.enums, t.body, env)
    case t: Term.PartialFunction => BranchTree.Branch("partial", "⟨arg⟩", t.cases.toList.map(armOf(_, None, env)))
    // Nested methods/types are their own scope — do not expand; the call site stays a leaf.
    case _: Defn.Def | _: Defn.Object | _: Defn.Class | _: Defn.Trait => BranchTree.Sequence(Nil)
    case other                                                        => descend(other, env)
  }

  private def descend(tree: Tree, env: Env): BranchTree = {
    val branchy = tree.children.iterator
      .map(visit(_, env))
      .flatMap { case BranchTree.Sequence(cs) => cs; case n => List(n) }
      .filter(BranchTree.hasBranch)
      .toList
    branchy match {
      case Nil           => BranchTree.Leaf(posOf(tree), endOf(tree), lineOf(tree), textOf(tree))
      case single :: Nil => single
      case many          => BranchTree.Sequence(many)
    }
  }

  /** A for-comprehension desugars to `withFilter`/`map`, which scoverage records as one statement spanning the whole expression — so a trivial body
    * becomes one leaf over that span; a body with its own `if`/`match` keeps those.
    */
  private def forBranch(t: Tree, kind: String, armLabel: String, enums: List[Enumerator], body: Term, env: Env): BranchTree = {
    val arm = visit(body, env) match {
      case _: BranchTree.Leaf => BranchTree.Leaf(posOf(t), endOf(t), lineOf(body), textOf(body))
      case branchy            => branchy
    }
    BranchTree.Branch(kind, clip(enums.map(_.toString).mkString("; ")), List(BranchTree.Arm(armLabel, None, ConstantPool.empty, arm)))
  }

  private def armOf(c: Case, scrut: Option[Predicate.Expr], env: Env): BranchTree.Arm = {
    val patText                       = textOf(c.pat)
    val label                         = c.cond.fold(s"case $patText")(g => s"case $patText if ${textOf(g)}")
    val guard: Option[Predicate.Cond] = c.pat match {
      case l: Lit.Int    => scrut.map(s => Predicate.Cmp("==", s, Predicate.Num(l.value.toDouble)))
      case l: Lit.Long   => scrut.map(s => Predicate.Cmp("==", s, Predicate.Num(l.value.toDouble)))
      case Pat.Var(name) => for (g <- c.cond; s <- scrut; cnd <- condOf(g, env + (name.value -> s))) yield cnd
      case _             => None
    }
    val literals = litsOf(c.pat) ++ c.cond.fold(ConstantPool.empty)(litsOf)
    BranchTree.Arm(label, guard, literals, visit(c.body, env))
  }

  /** Literals mentioned anywhere in a guard expression, by type — the pool the satisfying arm carries. */
  private def litsOf(t: Tree): ConstantPool =
    t.collect { case l: Lit => l }.foldLeft(ConstantPool.empty) {
      case (p, l: Lit.Int)    => p.copy(ints = p.ints + l.value)
      case (p, l: Lit.Long)   => p.copy(longs = p.longs + l.value)
      case (p, l: Lit.Double) => p.copy(doubles = p.doubles + l.value.asInstanceOf[Double]) // Lit.Double.value is typed Any in 4.17
      case (p, l: Lit.String) => p.copy(strings = p.strings + l.value)
      case (p, _)             => p
    }

  // ── Scalameta guard → numeric Predicate (the gradient's subset; anything else ⇒ None) ──────────

  private def condOf(t: Tree, env: Env): Option[Predicate.Cond] = t match {
    case t: Term.ApplyInfix =>
      t.argClause.values match {
        case List(r) =>
          t.op.value match {
            case "&&"                                         => for (a <- condOf(t.lhs, env); b <- condOf(r, env)) yield Predicate.And(a, b)
            case "||"                                         => for (a <- condOf(t.lhs, env); b <- condOf(r, env)) yield Predicate.Or(a, b)
            case op @ ("==" | "!=" | "<" | "<=" | ">" | ">=") => for (a <- exprOf(t.lhs, env); b <- exprOf(r, env)) yield Predicate.Cmp(op, a, b)
            case _                                            => None
          }
        case _ => None
      }
    case t: Term.ApplyUnary if t.op.value == "!" => condOf(t.arg, env).map(Predicate.Not)
    // A bare boolean parameter used as a condition (e.g. `if (enabled)`): true ⇔ its 0/1 encoding ≠ 0.
    case n: Term.Name => env.get(n.value).map(e => Predicate.Cmp("!=", e, Predicate.Num(0.0)))
    case _            => None
  }

  private def exprOf(t: Tree, env: Env): Option[Predicate.Expr] = t match {
    case n: Term.Name                                              => env.get(n.value)
    case l: Lit.Int                                                => Some(Predicate.Num(l.value.toDouble))
    case l: Lit.Long                                               => Some(Predicate.Num(l.value.toDouble))
    case l: Lit.Double                                             => Some(Predicate.Num(l.value.asInstanceOf[Double]))
    case t: Term.ApplyUnary if t.op.value == "-"                   => exprOf(t.arg, env).map(Predicate.Unary("neg", _))
    case t: Term.ApplyInfix if Set("+", "-", "*", "%")(t.op.value) =>
      t.argClause.values match {
        case List(r) => for (a <- exprOf(t.lhs, env); b <- exprOf(r, env)) yield Predicate.Binary(t.op.value, a, b)
        case _       => None
      }
    case Term.Select(q, name) if name.value == "abs"                            => exprOf(q, env).map(Predicate.Unary("abs", _))
    case Term.Select(q, name) if Set("toLong", "toInt", "toDouble")(name.value) => exprOf(q, env) // numeric coercion is identity here
    case t: Term.Apply                                                          =>                // math.abs(e)
      t.fun match {
        case Term.Select(Term.Name("math"), name) if name.value == "abs" =>
          t.argClause.values match {
            case List(x) => exprOf(x, env).map(Predicate.Unary("abs", _))
            case _       => None
          }
        case _ => None
      }
    case _ => None
  }

  private def posOf(t: Tree): Pos     = t.pos.start
  private def endOf(t: Tree): Pos     = t.pos.end
  private def lineOf(t: Tree): Int    = t.pos.startLine + 1 // Scalameta lines are 0-based; scoverage and editors are 1-based
  private def textOf(t: Tree): String = clip(t.toString)
  private def clip(s: String): String = s.replaceAll("\\s+", " ").trim.take(80)
}
