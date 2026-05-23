package benchmark.int

import benchmark.util.NumberProps

/** `Int`-input benchmark — value-rarity gradient, ordered shallow → deep.
  *
  * Under uniform `Arbitrary[Int]` ScalaCheck mixes in boundary specials (`0`, `1`, `-1`,
  * `Int.MinValue`, `Int.MaxValue`); predicates aligned with them are easy. Predicates that depend
  * on **structural properties of the number** (primality, Fibonacci membership, perfect powers,
  * palindromic digits) hold for only a sparse subset of `Int`, none of which coincide with the
  * specials beyond `MaxValue` (which is a Mersenne prime). Compounded across nesting levels,
  * these properties produce deep arms that random PBT cannot reach.
  *
  * Sections, top to bottom: trivial → easy → medium (single number properties) → medium-hard
  * (multi-integer relationships and primality bands) → hard (compound multi-condition trees).
  */
object IntBench {

  // ── Trivial baseline: random saturates ───────────────────────────────────

  def isPositive(n: Int): String =
    if (n > 0) "positive" else "non-positive"

  // ── Easy: structural arms reachable via boundary specials ────────────────

  /** Three outcomes; `zero` arm hits because `0` is a boundary special. */
  def sign(n: Int): String =
    if (n > 0) "positive"
    else if (n < 0) "negative"
    else "zero"

  // ── Modular arithmetic: rare arms without specific-value literals ────────

  /** Three arms; `divisible` (~1%) and `lucky` (~0.5%) are borderline in 100 inputs. */
  def mod97(n: Int): String =
    if (n % 97 == 0) "divisible"
    else if (n % 97 == 13) "lucky"
    else "ordinary"

  /** Two arms; `round` requires `n` a non-zero multiple of 1000 (~0.1%). */
  def divisibleByThousand(n: Int): String =
    if (n != 0 && n % 1000 == 0) "round" else "other"

  // ── Single number properties: 3-4 arms each, ~1 unreachable ──────────────

  /** Trivial range captures small boundary specials; for multi-digit `n`, palindromic-digit
    * values are very sparse.
    */
  def isPalindromeNumber(n: Int): String = {
    val s = n.toLong.abs.toString
    if (s.length <= 2) "trivial"
    else if (NumberProps.isDigitPalindrome(n.toLong)) "palindrome"
    else "non-palindrome"
  }

  /** Trivial range captures `0`, `1`; for `n >= 4`, perfect squares are ~√n / n — sparse. */
  def isPerfectSquare(n: Int): String =
    if (n < 0) "negative"
    else if (n < 4) "trivial"
    else if (NumberProps.isSquare(n.toLong)) "perfect-square"
    else "not-square"

  /** Trivial range captures `0`, `1`, `-1`; for `n > 1`, only 30 powers of two in `Int` range —
    * effectively unreachable.
    */
  def isPowerOfTwo(n: Int): String =
    if (n <= 1) "trivial"
    else if ((n & (n - 1)) == 0) "power-of-two"
    else "not-power"

  // ── Match with literals (acknowledged showcase) ──────────────────────────

  /** Five-arm `match`. `0` reachable via boundary special, `42` not. */
  def classify(n: Int): String = n match {
    case 0                  => "zero"
    case 42                 => "answer"
    case x if x < 0         => "negative"
    case x if x > 1_000_000 => "big-positive"
    case _                  => "small-positive"
  }

  /** Five arms; `42`, `1729` are non-boundary literals (unreached), `-1` boundary (reached). */
  def magicNumbers(n: Int): String =
    if (n == 42) "answer"
    else if (n == 1729) "ramanujan"
    else if (n == -1) "negative-one"
    else if (n < 0) "negative"
    else "ordinary"

  // ── Medium-hard: number-theoretic property + magnitude band ──────────────

  /** Three magnitude bands, primality inside each. Small primes (2..97) and medium primes
    * (100..9999) sit in input ranges with no boundary specials, so they're unreachable; only the
    * large band's `large-prime` arm is hit (via random large primes; `MaxValue` itself is the
    * Mersenne prime M31).
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

  /** Trivial captures `0`, `1`; for `n >= 2`, Fibonacci members are 30 values in `Int` range and
    * none coincide with boundary specials, so the `*-fib` arms are unreachable.
    */
  def isFibonacci(n: Int): String =
    if (n < 0) "negative"
    else if (n < 2) "trivial"
    else if (NumberProps.isFibonacci(n)) {
      if (n < 100) "small-fib"
      else if (n < 10000) "medium-fib"
      else "large-fib"
    } else "non-fib"

  // ── Multi-integer relationships ──────────────────────────────────────────

  /** Four arms. The trivial range (`|a|` ≤ 1 or `|b|` ≤ 1) captures boundary-special pairs.
    * For larger magnitudes, `b | a` and `a | b` are vanishing under independent uniform draws.
    */
  def divisibilityRelation(a: Int, b: Int): String =
    if (a.toLong.abs <= 1L || b.toLong.abs <= 1L) "trivial"
    else if (a % b == 0) "b-divides-a"
    else if (b % a == 0) "a-multiple-of-b-rev"
    else "no-divides"

  /** Three-integer compound classification: a magnitude floor, then an "all-prime" gate,
    * then equality structure within the all-prime sub-tree. Random uniform `Int` triples
    * almost never have all three elements prime (`P ≈ 0.05³`), so the entire all-prime
    * sub-tree — and its `all-equal-primes`, `two-equal-primes`, `distinct-primes` leaves
    * — are unreachable.
    */
  def tripleProperty(a: Int, b: Int, c: Int): String =
    if (a < 2 || b < 2 || c < 2) "trivial"
    else if (NumberProps.isPrime(a) && NumberProps.isPrime(b) && NumberProps.isPrime(c)) {
      if (a == b && b == c) "all-equal-primes"
      else if (a == b || b == c || a == c) "two-equal-primes"
      else "distinct-primes"
    } else if (NumberProps.isPrime(a) || NumberProps.isPrime(b) || NumberProps.isPrime(c))
      "some-prime"
    else "no-primes"

  // ── Hard: compound multi-condition trees with deeper nesting ─────────────

  /** Triangle classification from three side lengths. Random uniform `Int` rarely produces
    * triples that satisfy the triangle inequality, so the entire `valid-*` sub-tree is
    * unreachable.
    */
  def triangleType(a: Int, b: Int, c: Int): String =
    if (a <= 0 || b <= 0 || c <= 0) "invalid"
    else if (a + b <= c || a + c <= b || b + c <= a) "degenerate"
    else if (a == b && b == c) "equilateral"
    else if (a == b || b == c || a == c) "isoceles"
    else "scalene"

  /** Sign → magnitude pair → equality/divisibility. The "equal" arms can hit via paired boundary
    * specials (e.g. both `MaxValue`); the divisibility arms inside the both-large gate are
    * vanishing.
    */
  def deepIntPair(a: Int, b: Int): String =
    if (a > 0 && b > 0) {
      if (a > 1000 && b > 1000) {
        if (a == b) "equal-large-positive"
        else if (a % b == 0) "a-multiple-of-b"
        else if (b % a == 0) "b-multiple-of-a"
        else "coprime-like"
      } else "small-positive-pair"
    } else if (a < 0 && b < 0) {
      if (a < -1000 && b < -1000) {
        if (a == b) "equal-large-negative"
        else "distinct-large-negative"
      } else "small-negative-pair"
    } else "mixed-signs-or-zero"

  /** Deepest single-int tree: sign → magnitude → number-theoretic property → divisibility.
    * The perfect-square and palindromic-digit sub-trees are unreachable for large random `n`,
    * which makes 9+ source branches unreachable.
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

  /** Deepest three-int tree: positive-triple → additive dependency → triangle inequality →
    * triangle subtype. The valid-triangle sub-tree is unreachable in full.
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
