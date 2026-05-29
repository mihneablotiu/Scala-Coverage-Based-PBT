package benchmark.util

import scala.annotation.tailrec

/** Number-theoretic predicates shared by the benchmarks. All cheap enough to call per fuzz iteration: trial division for `isPrime`, a precomputed
  * `Set` for `isFibonacci`, integer sqrt for `isSquare`, decimal reversal for `isDigitPalindrome`, a capped Collatz loop for `collatzStepsBounded`.
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

  def isDigitPalindrome(n: Long): Boolean = {
    val s = n.abs.toString
    s == s.reverse
  }

  def collatzStepsBounded(start: Long, limit: Int): Int = {
    @tailrec def go(x: Long, count: Int): Int =
      if (x == 1L) count
      else if (count >= limit) -1
      else if (x % 2L == 0L) go(x / 2L, count + 1)
      else go(3L * x + 1L, count + 1)
    go(start, 0)
  }

  private val FibSet: Set[Int] =
    Iterator
      .iterate((0L, 1L)) { case (a, b) => (b, a + b) }
      .map(_._1)
      .takeWhile(_ <= Int.MaxValue.toLong)
      .map(_.toInt)
      .toSet
}
