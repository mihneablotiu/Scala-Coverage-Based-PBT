package benchmark.int

import benchmark.util.NumberProps

/** `Int`-input benchmark, ordered shallow → deep and grouped by the number of unreached arms random PBT typically leaves behind: saturated, one
  * unreached, two-or-three unreached, four+ unreached.
  */
object IntBench {

  // ── Saturated ────────────────────────────────────────────────────────────

  def isPositive(n: Int): String =
    if (n > 0) "positive" else "non-positive"

  def parity(n: Int): String =
    if (n % 2 == 0) "even" else "odd"

  def sign(n: Int): String =
    if (n > 0) "positive"
    else if (n < 0) "negative"
    else "zero"

  def mod97(n: Int): String =
    if (n % 97 == 0) "divisible"
    else if (n % 97 == 13) "lucky"
    else "ordinary"

  // ── One unreached arm ────────────────────────────────────────────────────

  def isPalindromeNumber(n: Int): String = {
    val s = n.toLong.abs.toString
    if (s.length <= 2) "trivial"
    else if (NumberProps.isDigitPalindrome(n.toLong)) "palindrome"
    else "non-palindrome"
  }

  def classify(n: Int): String = n match {
    case 0                  => "zero"
    case 42                 => "answer"
    case x if x < 0         => "negative"
    case x if x > 1_000_000 => "big-positive"
    case _                  => "small-positive"
  }

  // ── Two or three unreached arms ──────────────────────────────────────────

  def divisibleByThousand(n: Int): String =
    if (n == 0) "zero"
    else if (n % 1000 == 0) {
      if (n > 0) "positive-round"
      else "negative-round"
    } else "other"

  def signedPerfectSquare(n: Int): String =
    if (n.toLong.abs < 100L) "small-magnitude"
    else if (n > 0) {
      if (NumberProps.isSquare(n.toLong)) "positive-large-square"
      else "positive-large-non-square"
    } else {
      if (NumberProps.isSquare(-n.toLong)) "negative-large-square-abs"
      else "negative-large-non-square"
    }

  def parityPlusSquare(n: Int): String =
    if (n.toLong.abs < 100L) "small"
    else if (n % 2 == 0) {
      if (NumberProps.isSquare(n.toLong)) "even-square"
      else "even-non-square"
    } else {
      if (NumberProps.isSquare(n.toLong)) "odd-square"
      else "odd-non-square"
    }

  def divisibilityRelation(a: Int, b: Int): String =
    if (a.toLong.abs <= 1L || b.toLong.abs <= 1L) "trivial"
    else if (a % b == 0) "b-divides-a"
    else if (b % a == 0) "a-divides-b"
    else "no-divides"

  // ── Four+ unreached arms ─────────────────────────────────────────────────

  def signedPalindrome(n: Int): String = {
    val s = n.toLong.abs.toString
    if (s.length <= 2) "trivial"
    else if (NumberProps.isDigitPalindrome(n.toLong)) {
      if (n > 0) "positive-palindrome"
      else "negative-palindrome"
    } else "non-palindrome"
  }

  def magicNumbers(n: Int): String =
    if (n == 42) "answer"
    else if (n == 1729) "ramanujan"
    else if (n == -1) "negative-one"
    else if (n < 0) "negative"
    else "ordinary"

  def isPrime(n: Int): String =
    if (n < 2) "below-two"
    else if (n < 100) {
      if (NumberProps.isPrime(n)) "small-prime"
      else "small-composite"
    } else if (n < 10000) {
      if (NumberProps.isPrime(n)) "medium-prime"
      else "medium-composite"
    } else {
      if (NumberProps.isPrime(n)) "large-prime"
      else "large-composite"
    }

  def isFibonacci(n: Int): String =
    if (n < 0) "negative"
    else if (n < 2) "trivial"
    else if (NumberProps.isFibonacci(n)) {
      if (n < 100) "small-fib"
      else if (n < 10000) "medium-fib"
      else "large-fib"
    } else "non-fib"

  def collatzClass(n: Int): String =
    if (n <= 0) "non-positive"
    else if (n == 1) "one"
    else {
      val steps = NumberProps.collatzStepsBounded(n.toLong, 2000)
      if (steps < 0) "diverges"
      else if (n < 100) {
        if (steps < 10) "small-short" else "small-long"
      } else if (n < 1_000_000) {
        if (steps < 50) "medium-short" else "medium-typical"
      } else {
        if (steps < 100) "large-fast"
        else if (steps > 500) "large-very-long"
        else "large-typical"
      }
    }

  def triangleType(a: Int, b: Int, c: Int): String =
    if (a <= 0 || b <= 0 || c <= 0) "invalid"
    else if (a + b <= c || a + c <= b || b + c <= a) "degenerate"
    else if (a == b && b == c) "equilateral"
    else if (a == b || b == c || a == c) "isoceles"
    else "scalene"

  def deepIntClassify(n: Int): String =
    if (n > 0) {
      if (n > 1000) {
        if (NumberProps.isSquare(n.toLong)) {
          if (n % 7 == 0) "large-square-mult-7"
          else "large-square-other"
        } else if (NumberProps.isDigitPalindrome(n.toLong)) {
          if (n % 2 == 0) "large-palindrome-even"
          else "large-palindrome-odd"
        } else if (n % 1000 == 0) {
          if (n % 7 == 0) "large-thousand-mult-7"
          else "large-thousand-other"
        } else "large-other"
      } else "small-positive"
    } else "non-positive"

  def deepIntTriple(a: Int, b: Int, c: Int): String =
    if (a > 0 && b > 0 && c > 0) {
      if (a + b == c || a + c == b || b + c == a) "additively-dependent"
      else if (a + b > c && a + c > b && b + c > a) {
        if (a == b && b == c) "valid-equilateral"
        else if (a == b || b == c || a == c) "valid-isoceles"
        else "valid-scalene"
      } else "fails-triangle-inequality"
    } else "has-non-positive"
}
