package benchmark.int

import benchmark.util.NumberProps

/** `Int`-input benchmark — ordered shallow → deep, grouped roughly by the *number of unreached
  * arms* random PBT leaves behind.
  *
  * Sections:
  *   - **Saturated** (random hits every arm) — trivial baselines.
  *   - **One unreached arm** — single rare condition.
  *   - **Two or three unreached arms** — structurally rare conditions split into sub-arms so the
  *     unreached leaves sit in distinct sub-trees.
  *   - **Four+ unreached arms** — compound trees and algorithm-driven classification.
  */
object IntBench {

  // ── Saturated: random covers every arm ───────────────────────────────────

  def isPositive(n: Int): String =
    if (n > 0) "positive" else "non-positive"

  def parity(n: Int): String =
    if (n % 2 == 0) "even" else "odd"

  /** Three outcomes — `zero` hits because `0` is a boundary special. */
  def sign(n: Int): String =
    if (n > 0) "positive"
    else if (n < 0) "negative"
    else "zero"

  /** Three modular arms, all reachable in 100 inputs given the moderate moduli. */
  def mod97(n: Int): String =
    if (n % 97 == 0) "divisible"
    else if (n % 97 == 13) "lucky"
    else "ordinary"

  // ── One unreached arm ────────────────────────────────────────────────────

  /** Three arms: trivial range (boundary), palindromic digits (unreachable), other. */
  def isPalindromeNumber(n: Int): String = {
    val s = n.toLong.abs.toString
    if (s.length <= 2) "trivial"
    else if (NumberProps.isDigitPalindrome(n.toLong)) "palindrome"
    else "non-palindrome"
  }

  /** Five-arm `match`. Literal `42` is unreachable; everything else is hit. */
  def classify(n: Int): String = n match {
    case 0                  => "zero"
    case 42                 => "answer"
    case x if x < 0         => "negative"
    case x if x > 1_000_000 => "big-positive"
    case _                  => "small-positive"
  }

  // ── Two or three unreached arms, spread across sub-trees ─────────────────

  /** Four arms. Once the `divisible by 1000` filter fires (rare under random), the inner sign split
    * sends inputs to `positive-round` or `negative-round` — both unreachable, in distinct sub-trees
    * from `other`.
    */
  def divisibleByThousand(n: Int): String =
    if (n == 0) "zero"
    else if (n % 1000 == 0) {
      if (n > 0) "positive-round"
      else "negative-round"
    } else "other"

  /** Four arms. Trivial range captures boundary, then perfect-square check has two unreachable
    * sub-arms (positive-large-square, negative-large-square-abs), one in each sign sub-tree.
    */
  def signedPerfectSquare(n: Int): String =
    if (n.toLong.abs < 100L) "small-magnitude"
    else if (n > 0) {
      if (NumberProps.isSquare(n.toLong)) "positive-large-square"
      else "positive-large-non-square"
    } else {
      if (NumberProps.isSquare(-n.toLong)) "negative-large-square-abs"
      else "negative-large-non-square"
    }

  /** Five arms. After the parity split (both reach), the perfect-square check inside each parity
    * sub-tree is unreachable for large random `n` — so the *two* unreachable arms live in
    * *different* parity sub-trees rather than both on one side.
    */
  def parityPlusSquare(n: Int): String =
    if (n.toLong.abs < 100L) "small"
    else if (n % 2 == 0) {
      if (NumberProps.isSquare(n.toLong)) "even-square"
      else "even-non-square"
    } else {
      if (NumberProps.isSquare(n.toLong)) "odd-square"
      else "odd-non-square"
    }

  /** Five arms — three structural conditions on two integers. Boundary specials `0` and `1`/`-1`
    * are filtered out; inside the magnitude gate the two `multiple-of` arms are unreachable.
    */
  def divisibilityRelation(a: Int, b: Int): String =
    if (a.toLong.abs <= 1L || b.toLong.abs <= 1L) "trivial"
    else if (a % b == 0) "b-divides-a"
    else if (b % a == 0) "a-divides-b"
    else "no-divides"

  // ── Four+ unreached arms — compound trees and algorithm-driven ───────────

  /** Four arms. Trivial range captures boundary specials, then palindromic-digit numbers split by
    * sign — `positive-palindrome` and `negative-palindrome` are both unreached, sitting in
    * different sub-trees of the palindrome branch.
    */
  def signedPalindrome(n: Int): String = {
    val s = n.toLong.abs.toString
    if (s.length <= 2) "trivial"
    else if (NumberProps.isDigitPalindrome(n.toLong)) {
      if (n > 0) "positive-palindrome"
      else "negative-palindrome"
    } else "non-palindrome"
  }

  /** Five arms. `42` and `1729` are non-boundary literals (both unreached); `-1` is boundary. One
    * unreached arm sits in each of the literal sub-branches.
    */
  def magicNumbers(n: Int): String =
    if (n == 42) "answer"
    else if (n == 1729) "ramanujan"
    else if (n == -1) "negative-one"
    else if (n < 0) "negative"
    else "ordinary"

  /** Single-`Int` with magnitude bands × primality. Small primes and medium primes are unreachable
    * (their magnitude bands contain no boundary specials); only the large band's `large-prime` arm
    * hits via the Mersenne-prime `MaxValue`.
    */
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

  /** Trivial range / Fibonacci membership × magnitude bands. No chooseNum special is Fibonacci for
    * `n ≥ 2`, so the entire Fibonacci sub-tree is unreachable.
    */
  def isFibonacci(n: Int): String =
    if (n < 0) "negative"
    else if (n < 2) "trivial"
    else if (NumberProps.isFibonacci(n)) {
      if (n < 100) "small-fib"
      else if (n < 10000) "medium-fib"
      else "large-fib"
    } else "non-fib"

  /** Algorithm-driven: Collatz step count classified by `(magnitude band, step count band)`. The
    * unreached arms are distributed across *multiple* sub-trees:
    *
    *   - the top-level `diverges` (cap reached, very rare);
    *   - the small-`n` sub-tree (`small-short`, `small-long` — random doesn't produce `n` in [2,
    *     100)`);
    *   - the medium-`n` sub-tree (`medium-short`, `medium-typical` — `n` in `[100, 10⁶)`);
    *   - inside the large-`n` sub-tree, `large-fast` (Collatz < 100 steps) is also rare.
    */
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

  /** Triangle classification — five outcomes; only `invalid` and `degenerate` reach under uniform
    * random `Int`. The whole valid-triangle sub-tree (equilateral, isoceles, scalene) is
    * unreachable.
    */
  def triangleType(a: Int, b: Int, c: Int): String =
    if (a <= 0 || b <= 0 || c <= 0) "invalid"
    else if (a + b <= c || a + c <= b || b + c <= a) "degenerate"
    else if (a == b && b == c) "equilateral"
    else if (a == b || b == c || a == c) "isoceles"
    else "scalene"

  /** Sign / magnitude / number-theoretic property / divisibility — deeply nested classification
    * with 9+ unreached source branches; the unreached arms are split across the perfect-square,
    * palindromic-digit, and large-thousand sub-trees.
    */
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

  /** Three-`Int` deeply nested — positive-triple / additive dependency / triangle inequality /
    * triangle subtype. The valid-triangle sub-tree is unreached in full.
    */
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
