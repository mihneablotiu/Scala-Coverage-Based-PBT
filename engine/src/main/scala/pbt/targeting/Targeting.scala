package pbt.targeting

sealed trait NumericExpr

object NumericExpr {
  final case class Field(index: Int) extends NumericExpr

  final case class IntLiteral(value: Int)       extends NumericExpr
  final case class DoubleLiteral(value: Double) extends NumericExpr

  final case class Add(left: NumericExpr, right: NumericExpr)       extends NumericExpr
  final case class Sub(left: NumericExpr, right: NumericExpr)       extends NumericExpr
  final case class Mul(left: NumericExpr, right: NumericExpr)       extends NumericExpr
  final case class Div(left: NumericExpr, right: NumericExpr)       extends NumericExpr
  final case class Remainder(left: NumericExpr, right: NumericExpr) extends NumericExpr
  final case class Neg(value: NumericExpr)                          extends NumericExpr
}

sealed trait NumericPredicate

object NumericPredicate {
  final case class Compare(left: NumericExpr, op: CompareOp, right: NumericExpr) extends NumericPredicate
  final case class And(left: NumericPredicate, right: NumericPredicate)          extends NumericPredicate
  final case class Or(left: NumericPredicate, right: NumericPredicate)           extends NumericPredicate
}

sealed trait CompareOp

object CompareOp {
  case object Eq  extends CompareOp
  case object Neq extends CompareOp
  case object Lt  extends CompareOp
  case object Lte extends CompareOp
  case object Gt  extends CompareOp
  case object Gte extends CompareOp
}

sealed trait BranchGoalSide

object BranchGoalSide {
  case object MakeTrue  extends BranchGoalSide
  case object MakeFalse extends BranchGoalSide
}

final case class ExtractedPredicate(
    trueStart: Int,
    trueEnd: Int,
    falseStart: Int,
    falseEnd: Int,
    trueConditions: List[BranchCondition],
    falseConditions: List[BranchCondition]
)

final case class BranchCondition(
    predicate: NumericPredicate,
    side: BranchGoalSide
)

final case class BranchGoal(
    id: Int,
    coverageId: Int,
    conditions: List[BranchCondition]
)

final case class BestAttempt[A](
    input: A,
    distance: BigDecimal,
    iteration: Int
)
