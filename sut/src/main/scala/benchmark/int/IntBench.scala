package benchmark.int

/** `Int`-input benchmark spanning the **value-rarity** gradient.
  *
  * Each method is annotated with the approximate probability that the "rare" arm fires under a
  * uniform `Gen.chooseNum(Int.MinValue, Int.MaxValue)`. Expected hits per 100 inputs make the
  * thesis story concrete:
  *
  *   - **Trivial** (~50%) — both arms cover almost immediately.
  *   - **Moderate** (10⁻² – 10⁻³) — random saturates within 100 inputs.
  *   - **Hard** (10⁻⁴ – 10⁻⁸) — random typically misses; coverage-guided needs to win here.
  *   - **Effectively unreachable** (1/2³²) — neither will hit under uniform sampling; here a guided
  *     strategy is *forced* to use the coverage signal to construct the rare value, or it loses
  *     too.
  */
object IntBench {

  // ── Trivial (~50% per arm) ───────────────────────────────────────────────

  def isPositive(n: Int): String =
    if (n > 0) "positive" else "non-positive"

  def parity(n: Int): String =
    if (n % 2 == 0) "even" else "odd"

  /** Three-way: positive (~50%) / negative (~50%) / zero (1/2³² — vanishing). */
  def sign(n: Int): String =
    if (n > 0) "positive"
    else if (n < 0) "negative"
    else "zero"

  // ── Moderate (~10⁻² – 10⁻³) ──────────────────────────────────────────────

  /** ~1% — one residue class out of 97. Random hits a few times per 100 inputs. */
  def mod97is13(n: Int): String =
    if (n % 97 == 13) "lucky" else "normal"

  /** ~0.1% — non-zero multiples of 1000. Random borderline in 100 inputs. */
  def divisibleByThousand(n: Int): String =
    if (n % 1000 == 0) "round" else "other"

  // ── Hard (~10⁻⁴ – 10⁻⁸) ──────────────────────────────────────────────────

  /** ~2.3·10⁻⁸ — 100 values out of 2³². Random fails in 100 inputs; this is one of the headline
    * "random's blind spot" cases the thesis is built on.
    */
  def inSmallRange(n: Int): String =
    if (n >= 0 && n < 100) "small" else "outside"

  /** ~7·10⁻⁹ — the 30 positive powers of two representable in `Int`. */
  def isPowerOfTwo(n: Int): String =
    if (n > 0 && (n & (n - 1)) == 0) "power-of-two" else "not"

  // ── Effectively unreachable for random (1/2³²) ───────────────────────────

  /** The literal-match case. Random will not hit this in any reasonable number of inputs. */
  def isMagic(n: Int): String =
    if (n == 42) "answer" else "ordinary"

  // ── Compound: nested ifs with mixed-difficulty arms ──────────────────────

  /** Four nested decision points. The `n == 42` arm is the unreachable one; the rest spread across
    * easy/moderate. The thesis-relevant question is whether a guided strategy can isolate the rare
    * arm without sacrificing the easy ones.
    */
  def category(n: Int): String = {
    if (n > 100) {
      if (n % 7 == 0) "big-lucky" else "big"
    } else if (n == 42) "answer"
    else if (n > 0) "small"
    else "non-positive"
  }

  // ── Multi-arm match: 5 cases, three difficulty tiers ─────────────────────

  /** Five-arm `match` — exercises non-`if` branch coverage and mixes:
    *   - two **unreachable** literal arms (`0`, `42` — each 1/2³²),
    *   - two **easy** wide arms (`< 0` ≈ 50%, `> 10⁶` ≈ 50%),
    *   - one **rare-but-reachable** arm (`small-positive`, ≈ 2.3·10⁻⁴).
    *
    * Good stress test for any branch-arm prioritisation a guided strategy might do.
    */
  def classify(n: Int): String = n match {
    case 0                  => "zero"
    case 42                 => "answer"
    case x if x < 0         => "negative"
    case x if x > 1_000_000 => "big-positive"
    case _                  => "small-positive"
  }
}
