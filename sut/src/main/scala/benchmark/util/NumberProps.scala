package benchmark.util

/** Number-theoretic predicates shared between `IntBench` and `ListBench`. Each is cheap enough
  * to call per fuzz iteration:
  *
  *   - `isPrime` runs a `sqrt(n)`-bounded trial division — ~46k modular checks for `Int.MaxValue`,
  *     sub-millisecond in practice.
  *   - `isFibonacci` looks up a precomputed `Set[Int]` of the ~47 Fibonacci numbers that fit in
  *     `Int`.
  *   - `isSquare` / `isCube` use `math.sqrt` / `math.cbrt` plus an exact integer check.
  *   - `isDigitPalindrome` reverses the decimal-digit string of `abs(n)`.
  */
object NumberProps {

  def isPrime(n: Int): Boolean = {
    if (n < 2) false
    else if (n < 4) true
    else if (n % 2 == 0) false
    else (3 to math.sqrt(n.toDouble).toInt by 2).forall(d => n % d != 0)
  }

  def isFibonacci(n: Int): Boolean = n >= 0 && FibSet.contains(n)

  def isSquare(n: Long): Boolean = {
    if (n < 0L) false
    else {
      val r = math.sqrt(n.toDouble).toLong
      r * r == n
    }
  }

  def isCube(n: Long): Boolean = {
    val r = math.cbrt(n.toDouble).round
    r * r * r == n
  }

  def isDigitPalindrome(n: Long): Boolean = {
    val s = n.abs.toString
    s == s.reverse
  }

  private val FibSet: Set[Int] =
    Iterator
      .iterate((0L, 1L)) { case (a, b) => (b, a + b) }
      .map(_._1)
      .takeWhile(_ <= Int.MaxValue.toLong)
      .map(_.toInt)
      .toSet
}
