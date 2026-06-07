package pbt.targeting

import pbt.targeting.BranchGoalSide._
import pbt.targeting.CompareOp._
import pbt.targeting.NumericExpr._
import pbt.targeting.NumericPredicate._

object BranchDistance {
  private val Step = BigDecimal(1)

  def distance[A: OptionalNumericFields](goal: BranchGoal, input: A): Option[BigDecimal] =
    OptionalNumericFields[A].instance.flatMap(_.fields(input)).flatMap { fields =>
      goal.conditions
        .map(condition => distance(condition.predicate, condition.side, fields))
        .foldLeft(Option(BigDecimal(0))) {
          case (Some(total), Some(distance)) => Some(total + distance)
          case _                             => None
        }
    }

  def distances[A: OptionalNumericFields](goals: List[BranchGoal], input: A): Map[Int, BigDecimal] =
    goals.flatMap(goal => distance(goal, input).map(goal.id -> _)).toMap

  private def distance(predicate: NumericPredicate, side: BranchGoalSide, fields: Vector[BigDecimal]): Option[BigDecimal] =
    predicate match {
      case Compare(left, op, right) =>
        for {
          l <- eval(left, fields)
          r <- eval(right, fields)
        } yield compare(l, op, r, side)

      case And(left, right) if side == MakeTrue =>
        combine(_ + _, distance(left, MakeTrue, fields), distance(right, MakeTrue, fields))
      case And(left, right) =>
        combine(_ min _, distance(left, MakeFalse, fields), distance(right, MakeFalse, fields))

      case Or(left, right) if side == MakeTrue =>
        combine(_ min _, distance(left, MakeTrue, fields), distance(right, MakeTrue, fields))
      case Or(left, right) =>
        combine(_ + _, distance(left, MakeFalse, fields), distance(right, MakeFalse, fields))
    }

  private def eval(expr: NumericExpr, fields: Vector[BigDecimal]): Option[BigDecimal] =
    expr match {
      case Field(index) =>
        fields.lift(index)
      case IntLiteral(value) =>
        Some(BigDecimal(value))
      case DoubleLiteral(value) =>
        Some(BigDecimal.decimal(value))
      case Add(left, right) =>
        combine(_ + _, eval(left, fields), eval(right, fields))
      case Sub(left, right) =>
        combine(_ - _, eval(left, fields), eval(right, fields))
      case Mul(left, right) =>
        combine(_ * _, eval(left, fields), eval(right, fields))
      case Div(left, right) =>
        combineWhen(_ / _, eval(left, fields), eval(right, fields))(_ != 0)
      case Remainder(left, right) =>
        combineWhen(_ % _, eval(left, fields), eval(right, fields))(_ != 0)
      case Neg(value) =>
        eval(value, fields).map(-_)
    }

  private def compare(left: BigDecimal, op: CompareOp, right: BigDecimal, side: BranchGoalSide): BigDecimal =
    (op, side) match {
      case (Eq, MakeTrue)   => (left - right).abs
      case (Eq, MakeFalse)  => if (left != right) 0 else Step
      case (Neq, MakeTrue)  => if (left != right) 0 else Step
      case (Neq, MakeFalse) => (left - right).abs
      case (Lt, MakeTrue)   => if (left < right) 0 else left - right + Step
      case (Lt, MakeFalse)  => if (left >= right) 0 else right - left
      case (Lte, MakeTrue)  => if (left <= right) 0 else left - right
      case (Lte, MakeFalse) => if (left > right) 0 else right - left + Step
      case (Gt, MakeTrue)   => if (left > right) 0 else right - left + Step
      case (Gt, MakeFalse)  => if (left <= right) 0 else left - right
      case (Gte, MakeTrue)  => if (left >= right) 0 else right - left
      case (Gte, MakeFalse) => if (left < right) 0 else left - right + Step
    }

  private def combine(f: (BigDecimal, BigDecimal) => BigDecimal, left: Option[BigDecimal], right: Option[BigDecimal]): Option[BigDecimal] =
    for {
      l <- left
      r <- right
    } yield f(l, r)

  private def combineWhen(
      f: (BigDecimal, BigDecimal) => BigDecimal,
      left: Option[BigDecimal],
      right: Option[BigDecimal]
  )(valid: BigDecimal => Boolean): Option[BigDecimal] =
    for {
      l <- left
      r <- right
      if valid(r)
    } yield f(l, r)
}
