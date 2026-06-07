package pbt.analysis

import pbt.gen.ConstantPool
import pbt.targeting.BranchGoalSide._
import pbt.targeting.CompareOp._
import pbt.targeting.NumericExpr._
import pbt.targeting.NumericPredicate._
import pbt.targeting.{BranchCondition, CompareOp, ExtractedPredicate, NumericExpr, NumericPredicate}

import java.nio.file.{Files, Path}
import scala.meta._

object Parser {

  def literalPool(sourceFile: Path, method: String): Option[ConstantPool] =
    methodDef(sourceFile, method).map(d => mineLiterals(d.body))

  def numericPredicates(sourceFile: Path, method: String): List[ExtractedPredicate] =
    methodDef(sourceFile, method).fold(List.empty[ExtractedPredicate]) { methodDef =>
      val params = paramEnv(methodDef)
      val values = valueEnv(methodDef.body, params)

      numericPredicates(methodDef.body, params, values, Nil)
    }

  private def methodDef(sourceFile: Path, method: String): Option[Defn.Def] =
    Files
      .readString(sourceFile)
      .parse[Source]
      .toOption
      .flatMap(_.collect { case d: Defn.Def if d.name.value == method => d }.headOption)

  private def mineLiterals(t: Tree): ConstantPool =
    t.collect { case l: Lit => l }.foldLeft(ConstantPool.empty) {
      case (p, Lit.Int(value))     => p.copy(ints = p.ints + value)
      case (p, Lit.Double(value))  => value.toDoubleOption.fold(p)(d => p.copy(doubles = p.doubles + d))
      case (p, Lit.String(value))  => p.copy(strings = p.strings + value)
      case (p, Lit.Boolean(value)) => p.copy(booleans = p.booleans + value)
      case (p, _)                  => p
    }

  private def paramEnv(method: Defn.Def): Map[String, Int] =
    method.paramClauseGroups
      .flatMap(_.paramClauses)
      .flatMap(_.values)
      .collect { case param if numericType(param.decltpe) => param.name.value }
      .zipWithIndex
      .toMap

  private def valueEnv(body: Tree, params: Map[String, Int]): Map[String, NumericExpr] =
    body.collect { case Defn.Val(_, List(Pat.Var(Term.Name(name))), _, rhs) => name -> rhs }.foldLeft(Map.empty[String, NumericExpr]) {
      case (values, (name, rhs)) =>
        numericExpr(rhs, params, values).fold(values)(expr => values.updated(name, expr))
    }

  private def numericType(tpe: Option[Type]): Boolean =
    tpe.flatMap(typeName).exists(Set("Int", "Double"))

  private def typeName(tpe: Type): Option[String] =
    tpe match {
      case Type.Name(name)                 => Some(name)
      case Type.Select(_, Type.Name(name)) => Some(name)
      case _                               => None
    }

  private def numericPredicate(term: Term, params: Map[String, Int], values: Map[String, NumericExpr]): Option[NumericPredicate] =
    term match {
      case Infix(left, "&&", right) =>
        combine(And.apply, numericPredicate(left, params, values), numericPredicate(right, params, values))
      case Infix(left, "||", right) =>
        combine(Or.apply, numericPredicate(left, params, values), numericPredicate(right, params, values))
      case Infix(left, op, right) =>
        compareOp(op).flatMap(compare =>
          combine((l: NumericExpr, r: NumericExpr) => Compare(l, compare, r), numericExpr(left, params, values), numericExpr(right, params, values))
        )
      case _ =>
        None
    }

  private def numericPredicates(
      tree: Tree,
      params: Map[String, Int],
      values: Map[String, NumericExpr],
      path: List[BranchCondition]
  ): List[ExtractedPredicate] =
    tree match {
      case branch: Term.If =>
        val condition  = numericPredicate(branch.cond, params, values).filter(inputDependent)
        val truePath   = condition.fold(path)(predicate => path :+ BranchCondition(predicate, MakeTrue))
        val falsePath  = condition.fold(path)(predicate => path :+ BranchCondition(predicate, MakeFalse))
        val extracted  = condition.flatMap(_ => positioned(branch, truePath, falsePath)).toList
        val nestedThen = numericPredicates(branch.thenp, params, values, truePath)
        val nestedElse = numericPredicates(branch.elsep, params, values, falsePath)
        extracted ++ nestedThen ++ nestedElse
      case other =>
        other.children.flatMap(child => numericPredicates(child, params, values, path)).toList
    }

  private def numericExpr(term: Term, params: Map[String, Int], values: Map[String, NumericExpr]): Option[NumericExpr] =
    term match {
      case Term.Name(name) =>
        values.get(name).orElse(params.get(name).map(Field.apply))
      case Lit.Int(value) =>
        Some(IntLiteral(value))
      case Lit.Double(value) =>
        value.toDoubleOption.map(DoubleLiteral.apply)
      case Term.ApplyUnary(Term.Name("-"), value) =>
        numericExpr(value, params, values).map(Neg.apply)
      case Infix(left, "+", right) =>
        combine(Add.apply, numericExpr(left, params, values), numericExpr(right, params, values))
      case Infix(left, "-", right) =>
        combine(Sub.apply, numericExpr(left, params, values), numericExpr(right, params, values))
      case Infix(left, "*", right) =>
        combine(Mul.apply, numericExpr(left, params, values), numericExpr(right, params, values))
      case Infix(left, "/", right) =>
        combine(Div.apply, numericExpr(left, params, values), numericExpr(right, params, values))
      case Infix(left, "%", right) =>
        combine(Remainder.apply, numericExpr(left, params, values), numericExpr(right, params, values))
      case _ =>
        None
    }

  private def compareOp(op: String): Option[CompareOp] =
    op match {
      case "==" => Some(Eq)
      case "!=" => Some(Neq)
      case "<"  => Some(Lt)
      case "<=" => Some(Lte)
      case ">"  => Some(Gt)
      case ">=" => Some(Gte)
      case _    => None
    }

  private def positioned(
      branch: Term.If,
      trueConditions: List[BranchCondition],
      falseConditions: List[BranchCondition]
  ): Option[ExtractedPredicate] =
    for {
      ifTrue  <- position(branch.thenp)
      ifFalse <- position(branch.elsep)
    } yield ExtractedPredicate(ifTrue._1, ifTrue._2, ifFalse._1, ifFalse._2, trueConditions, falseConditions)

  private def position(tree: Tree): Option[(Int, Int)] =
    Option.when(tree.pos != Position.None)(tree.pos.start -> tree.pos.end)

  private def inputDependent(predicate: NumericPredicate): Boolean =
    predicate match {
      case Compare(left, _, right) => inputDependent(left) || inputDependent(right)
      case And(left, right)        => inputDependent(left) || inputDependent(right)
      case Or(left, right)         => inputDependent(left) || inputDependent(right)
    }

  private def inputDependent(expr: NumericExpr): Boolean =
    expr match {
      case Field(_)               => true
      case IntLiteral(_)          => false
      case DoubleLiteral(_)       => false
      case Add(left, right)       => inputDependent(left) || inputDependent(right)
      case Sub(left, right)       => inputDependent(left) || inputDependent(right)
      case Mul(left, right)       => inputDependent(left) || inputDependent(right)
      case Div(left, right)       => inputDependent(left) || inputDependent(right)
      case Remainder(left, right) => inputDependent(left) || inputDependent(right)
      case Neg(value)             => inputDependent(value)
    }

  private def combine[A, B, C](f: (A, B) => C, left: Option[A], right: Option[B]): Option[C] =
    for {
      l <- left
      r <- right
    } yield f(l, r)

  private object Infix {
    def unapply(term: Term): Option[(Term, String, Term)] =
      term match {
        case Term.ApplyInfix.After_4_6_0(left, Term.Name(op), _, args) =>
          args.values match {
            case List(right) => Some((left, op, right))
            case _           => None
          }
        case _ =>
          None
      }
  }
}
