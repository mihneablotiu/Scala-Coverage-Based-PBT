package benchmark.int

/** Integer-input benchmark. Ten methods split between "easy" branches that random covers quickly
  * and "hard" branches whose true arm has very low probability under a uniform `Int` generator.
  */
object IntBench {

  // ── easy: random covers both arms quickly ──────────────────────────

  def isPositive(n: Int): String =
    if (n > 0) "positive" else "non-positive"

  def parity(n: Int): String =
    if (n % 2 == 0) "even" else "odd"

  def sign(n: Int): String =
    if (n > 0) "positive"
    else if (n < 0) "negative"
    else "zero"

  // ── hard: probabilistic branches ──────────────────────────────────

  /** True branch hit when `n mod 97 == 13` — about 1 % of all `Int`s. */
  def classify97(n: Int): String =
    if (n % 97 == 13) "lucky" else "normal"

  /** True branch hit only when `n == 42` — 1 in 2³² for a uniform `Int`. */
  def isMagic(n: Int): String =
    if (n == 42) "answer" else "ordinary"

  /** True branch hit when `0 <= n < 100` — ~2.3 % of all `Int`s. */
  def inSmallRange(n: Int): String =
    if (n >= 0 && n < 100) "small" else "outside"

  /** True branch hit when `n` is a non-zero multiple of 1000 — 0.1 %. */
  def divisibleByThousand(n: Int): String =
    if (n % 1000 == 0) "round" else "other"

  /** True branch hit only for the ~30 positive powers of two. */
  def powerOfTwo(n: Int): String =
    if (n > 0 && (n & (n - 1)) == 0) "power-of-two" else "not"

  /** Two ANDed predicates: `n > 0` (≈ 50 %) and `n % 13 == 0` (≈ 7.7 %). */
  def luckyPositive(n: Int): String =
    if (n > 0 && n % 13 == 0) "lucky-positive" else "other"

  /** Nested branches: 8 total, including a very-rare `n == 42` arm. */
  def category(n: Int): String = {
    if (n > 100) {
      if (n % 7 == 0) "big-lucky" else "big"
    } else if (n == 42) "answer"
    else if (n > 0) "small"
    else "non-positive"
  }
}
