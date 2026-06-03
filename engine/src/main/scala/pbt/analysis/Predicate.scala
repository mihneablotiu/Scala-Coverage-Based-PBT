package pbt.analysis

/** A tiny numeric condition language the [[Parser]] derives from `if`/`match` guards. It lets the gradient tactic compute a Korel/EvoSuite-style
  * **branch distance** — how far an input is from taking an uncovered branch — which it then hill-climbs.
  *
  * Distance is defined only for the universal, type-level comparisons (numeric `<,<=,>,>=,==,!=` and arithmetic), driven by the operand type and a
  * fixed operator set, so it works for any program. Anything else (strings, collections, derived values) is left unexpressed: that leaf gets no
  * gradient and the other tactics handle it. Parameters are referenced positionally; the tactic binds them from the input at runtime.
  */
object Predicate {

  sealed trait Expr
  final case class Par(idx: Int)                        extends Expr // i-th parameter, as a number
  final case class Num(value: Double)                   extends Expr
  final case class Unary(op: String, e: Expr)           extends Expr // "neg", "abs"
  final case class Binary(op: String, l: Expr, r: Expr) extends Expr // "+", "-", "*", "%"

  sealed trait Cond
  final case class Cmp(op: String, l: Expr, r: Expr) extends Cond // "==","!=","<","<=",">",">="
  final case class And(l: Cond, r: Cond)             extends Cond
  final case class Or(l: Cond, r: Cond)              extends Cond
  final case class Not(c: Cond)                      extends Cond

  /** The i-th parameter as a Double, or `None` if it isn't numeric (a list, string, …). */
  type Args = IndexedSeq[Option[Double]]

  /** Bind an input to its parameters: a tuple of matching arity splits componentwise, anything else is one parameter. Non-numeric components become
    * `None`, so guards over them are inexpressible.
    */
  def bind(input: Any, paramCount: Int): Args = {
    val raw: Seq[Any] = input match {
      case p: Product if paramCount > 1 && p.productArity == paramCount => p.productIterator.toSeq
      case other                                                        => Seq(other)
    }
    raw.map {
      case i: Int     => Some(i.toDouble)
      case l: Long    => Some(l.toDouble)
      case d: Double  => Some(d)
      case b: Boolean => Some(if (b) 1.0 else 0.0)
      case _          => None
    }.toIndexedSeq
  }

  /** Fitness of a leaf whose path requires all of `guards` to hold: the sum of each guard's distance (0 ⇔ satisfied). `None` if any is inexpressible.
    */
  def pathFitness(guards: List[Cond], args: Args): Option[Double] = {
    val ds = guards.map(distance(_, want = true, args))
    if (ds.exists(_.isEmpty)) None else Some(ds.flatten.sum)
  }

  /** Raw branch distance (>= 0, 0 ⇔ satisfied) of `cond` evaluating to `want`; `None` if it touches a non-numeric value. Un-normalised, so large gaps
    * still slope.
    */
  def distance(cond: Cond, want: Boolean, args: Args): Option[Double] = cond match {
    case Cmp(op, l, r) => for (a <- eval(l, args); b <- eval(r, args)) yield rawCmp(if (want) op else negate(op), a, b)
    case And(l, r)     => if (want) sum(distance(l, true, args), distance(r, true, args)) else min(distance(l, false, args), distance(r, false, args))
    case Or(l, r)      => if (want) min(distance(l, true, args), distance(r, true, args)) else sum(distance(l, false, args), distance(r, false, args))
    case Not(c)        => distance(c, !want, args)
  }

  private def eval(e: Expr, args: Args): Option[Double] = e match {
    case Par(i)           => if (i >= 0 && i < args.length) args(i) else None
    case Num(v)           => Some(v)
    case Unary("neg", x)  => eval(x, args).map(-_)
    case Unary("abs", x)  => eval(x, args).map(math.abs)
    case Unary(_, _)      => None
    case Binary(op, l, r) => for (a <- eval(l, args); b <- eval(r, args); v <- combine(op, a, b)) yield v
  }

  private def combine(op: String, a: Double, b: Double): Option[Double] = op match {
    case "+" => Some(a + b)
    case "-" => Some(a - b)
    case "*" => Some(a * b)
    case "%" => if (b != 0.0) Some(a % b) else None
    case _   => None
  }

  private def rawCmp(op: String, a: Double, b: Double): Double = op match {
    case "==" => math.abs(a - b)
    case "!=" => if (a != b) 0.0 else 1.0
    case "<"  => if (a < b) 0.0 else a - b + 1.0
    case "<=" => if (a <= b) 0.0 else a - b
    case ">"  => if (a > b) 0.0 else b - a + 1.0
    case ">=" => if (a >= b) 0.0 else b - a
    case _    => Double.NaN
  }

  private def negate(op: String): String = op match {
    case "==" => "!="; case "!=" => "=="; case "<" => ">="; case "<=" => ">"; case ">" => "<="; case ">=" => "<"; case other => other
  }

  private def sum(a: Option[Double], b: Option[Double]): Option[Double] = for (x <- a; y <- b) yield x + y
  private def min(a: Option[Double], b: Option[Double]): Option[Double] = (a, b) match {
    case (Some(x), Some(y)) => Some(math.min(x, y))
    case (Some(x), None)    => Some(x)
    case (None, Some(y))    => Some(y)
    case _                  => None
  }
}
