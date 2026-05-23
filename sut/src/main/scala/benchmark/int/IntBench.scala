package benchmark.int

/** `Int`-input benchmark — increasing difficulty across the file.
  *
  * Under uniform `Arbitrary[Int]` ScalaCheck mixes in **boundary specials**: `0`, `1`, `-1`,
  * `Int.MinValue`, `Int.MaxValue`. Predicates aligned with these specials are easy for random;
  * everything else falls on a gradient from moderate (modular arithmetic) to effectively
  * unreachable (number-theoretic properties of *large* values, value coincidences between
  * independent draws, three-side structural constraints).
  *
  * Sections, top to bottom: trivial → easy → moderate → multi-integer → number properties →
  * specific-literal showcases → compound multi-integer.
  */
object IntBench {

  // ── Trivial: random saturates ────────────────────────────────────────────

  def isPositive(n: Int): String =
    if (n > 0) "positive" else "non-positive"

  def parity(n: Int): String =
    if (n % 2 == 0) "even" else "odd"

  // ── Easy: structural arms reachable via boundary specials ────────────────

  /** Three outcomes; `zero` hits because `0` is a boundary special. */
  def sign(n: Int): String =
    if (n > 0) "positive"
    else if (n < 0) "negative"
    else "zero"

  /** Two arms; `huge` requires the magnitude to exceed 10⁹ (~53% under uniform `Int`). */
  def magnitudeClass(n: Int): String =
    if (n.toLong.abs > 1_000_000_000L) "huge" else "modest"

  // ── Moderate: modular arithmetic ─────────────────────────────────────────

  /** Three arms. `divisible` (~1%) and `lucky` (~0.5%) are borderline in 100 inputs. */
  def mod97(n: Int): String =
    if (n % 97 == 0) "divisible"
    else if (n % 97 == 13) "lucky"
    else "ordinary"

  /** Two arms; `round` needs a non-zero multiple of 1000 (~0.1%). */
  def divisibleByThousand(n: Int): String =
    if (n != 0 && n % 1000 == 0) "round" else "other"

  // ── Multi-integer: value coincidences between independent draws ──────────

  /** Three arms; `equal` is reachable via boundary specials but covers a ~2⁻³² slice otherwise.
    */
  def compare(a: Int, b: Int): String =
    if (a == b) "equal"
    else if (a > b) "first-bigger"
    else "second-bigger"

  /** Three arms; `zero-sum` needs `a == -b` — hit only when both draws are boundary specials
    * that cancel (e.g. `(1, -1)`).
    */
  def sumSign(a: Int, b: Int): String =
    if (a + b > 0) "positive-sum"
    else if (a + b < 0) "negative-sum"
    else "zero-sum"

  /** Five arms over two coordinates; the four open quadrants are ~25% each, `axis` is the
    * `x == 0 || y == 0` slice (boundary-reachable, but represents a sub-2⁻³¹ fraction).
    */
  def quadrant(x: Int, y: Int): String =
    if (x > 0 && y > 0) "I"
    else if (x < 0 && y > 0) "II"
    else if (x < 0 && y < 0) "III"
    else if (x > 0 && y < 0) "IV"
    else "axis"

  // ── Hard: number-theoretic properties (structural, no literal matches) ───

  /** Four arms. The trivial range (`n < 4`) captures boundary specials `0`, `1`. For
    * `n >= 4`, perfect squares are sparse (~√n out of n) — `perfect-square` is unreachable.
    */
  def isPerfectSquare(n: Int): String =
    if (n < 0) "negative"
    else if (n < 4) "trivial"
    else if (isSquare(n.toLong)) "perfect-square"
    else "not-square"

  /** Four arms. The trivial range (`|n| < 8`) captures boundary specials. For larger `|n|`,
    * perfect cubes are extremely sparse — `perfect-cube` is unreachable.
    */
  def isPerfectCube(n: Int): String =
    if (n > -8 && n < 8) "trivial"
    else if (isCube(n.toLong)) "perfect-cube"
    else "not-cube"

  /** Four arms. The trivial range (`n <= 1`) captures `0`, `1`, `-1`. For `n > 1`, powers of
    * two are 30 values in `[2, 2³¹)` — `power-of-two` is unreachable under uniform sampling.
    */
  def isPowerOfTwo(n: Int): String =
    if (n <= 1) "trivial"
    else if ((n & (n - 1)) == 0) "power-of-two"
    else "not-power"

  /** Three arms. The trivial range (`|n|` ≤ 2 digits) captures small boundary specials. For
    * multi-digit `n`, palindromic-digit values are very sparse — `palindrome` is unreachable.
    */
  def isPalindromeNumber(n: Int): String = {
    val s = n.toLong.abs.toString
    if (s.length <= 2) "trivial"
    else if (s == s.reverse) "palindrome"
    else "non-palindrome"
  }

  // ── Multi-integer relationships (structural) ─────────────────────────────

  /** Four arms. The trivial range (`|a|` ≤ 1 or `|b|` ≤ 1) captures boundary-special pairs.
    * For larger magnitudes, `b | a` and `a | b` are vanishing under independent uniform draws.
    */
  def divisibilityRelation(a: Int, b: Int): String =
    if (a.toLong.abs <= 1L || b.toLong.abs <= 1L) "trivial"
    else if (a % b == 0) "b-divides-a"
    else if (b % a == 0) "a-divides-b"
    else "no-divides"

  // ── Specific-literal showcases (deliberately few) ────────────────────────

  /** Five-arm `match` — two literal arms (`0` reachable via boundary, `42` not), three numeric
    * guards.
    */
  def classify(n: Int): String = n match {
    case 0                  => "zero"
    case 42                 => "answer"
    case x if x < 0         => "negative"
    case x if x > 1_000_000 => "big-positive"
    case _                  => "small-positive"
  }

  /** Five arms; literal arms `42` and `1729` are unreachable, `-1` is boundary-reachable. The
    * cleanest "random can't hit specific values" showcase.
    */
  def magicNumbers(n: Int): String =
    if (n == 42) "answer"
    else if (n == 1729) "ramanujan"
    else if (n == -1) "negative-one"
    else if (n < 0) "negative"
    else "ordinary"

  // ── Compound multi-integer (structural) ──────────────────────────────────

  /** Triangle classification from three side lengths. Five arms:
    *
    *   - `invalid` — at least one non-positive side (~87.5% under uniform `Int`).
    *   - `degenerate` — fails the triangle inequality; the common case among all-positive
    *     triples because random 32-bit values almost always have one side dwarfing the other
    *     two or sums that overflow.
    *   - `equilateral` (a == b == c, P ≈ 2⁻⁶⁴) — effectively unreachable.
    *   - `isoceles` (two equal, P ≈ 3·2⁻³²) — effectively unreachable.
    *   - `scalene` — what's left; vanishing under uniform `Int`.
    *
    * The strongest "random can't construct" thesis case in this file.
    */
  def triangleType(a: Int, b: Int, c: Int): String = {
    if (a <= 0 || b <= 0 || c <= 0) "invalid"
    else if (a + b <= c || a + c <= b || b + c <= a) "degenerate"
    else if (a == b && b == c) "equilateral"
    else if (a == b || b == c || a == c) "isoceles"
    else "scalene"
  }

  // ── Deeply nested: leaves compound 3-4 filters in sequence ───────────────

  /** Single-`Int` deeply nested classification. Four levels of filtering — sign, magnitude,
    * number-theoretic property, then divisibility — produce eight outcomes, of which five
    * require both a large magnitude *and* a rare structural property to hold. Random typically
    * reaches `non-positive`, `small-positive`, `large-other`, and the `large-thousand-*` arms
    * sometimes; the perfect-square and palindromic sub-trees are effectively unreachable.
    */
  def deepIntClassify(n: Int): String =
    if (n > 0) {
      if (n > 1000) {
        if (isSquare(n.toLong)) {
          if (n % 7 == 0) "large-square-mult-7"
          else "large-square-other"
        } else if (isDigitPalindrome(n.toLong)) {
          if (n % 2 == 0) "large-palindrome-even"
          else "large-palindrome-odd"
        } else if (n % 1000 == 0) {
          if (n % 7 == 0) "large-thousand-mult-7"
          else "large-thousand-other"
        } else "large-other"
      } else "small-positive"
    } else "non-positive"

  /** Two-`Int` deeply nested classification. Three sign-then-magnitude levels narrow the
    * `(a, b)` pair, then the deepest level asks for a structural relationship between two
    * independent draws (equality, divisibility). The equality arms can hit when both draws
    * land on the same boundary special (e.g. both `Int.MaxValue`), but the divisibility
    * arms inside the "both large" gate are vanishing.
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

  /** Three-`Int` deeply nested classification. Sign filter → additive dependency → triangle
    * inequality → triangle subtype. The whole "valid triangle" sub-tree is unreachable under
    * uniform random `Int` (same reasoning as [[triangleType]]), so the inner equilateral /
    * isoceles / scalene leaves are *all* unreached — plus the `additively-dependent` arm.
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

  // ── Helpers ──────────────────────────────────────────────────────────────

  private def isSquare(n: Long): Boolean = {
    val r = math.sqrt(n.toDouble).toLong
    r * r == n
  }

  private def isCube(n: Long): Boolean = {
    val r = math.cbrt(n.toDouble).round
    r * r * r == n
  }

  private def isDigitPalindrome(n: Long): Boolean = {
    val s = n.abs.toString
    s == s.reverse
  }
}
