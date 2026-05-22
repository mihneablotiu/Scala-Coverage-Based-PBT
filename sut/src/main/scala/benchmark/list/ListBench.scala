package benchmark.list

/** `List[Int]`-input benchmark spanning the **structural-rarity** gradient.
  *
  * Designed against a `Gen.listOf(Gen.choose(-5, 5))` — size-sensitive list of length 0..maxSize
  * with elements drawn from an 11-symbol alphabet. ScalaCheck's `Test.Parameters` schedule grows
  * the size from 0 to 100 across the iterations, so early inputs are short and later inputs are
  * long; this is what `forAll` would feed.
  *
  * **Size guards (`l.size >= K`) are deliberate**: without them, `Nil == Nil.sorted` and
  * `Nil == Nil.reverse` are both trivially true, so `Gen.listOf` would cover those arms with its
  * empty-list iteration alone — measuring "did random produce an empty list?" instead of "did
  * random find sortedness/palindromicity." With the guards, the rare arm actually requires the
  * structural property the method name advertises.
  *
  * The 11-symbol alphabet matters too: with full `Int`, predicates like `l.contains(0)` and
  * `firstIsLast` are unreachable for *any* strategy, so the comparison degenerates. With `-5..5`,
  * they're rare-but-reachable — the thesis-interesting zone.
  */
object ListBench {

  // ── Trivial — easy arms ──────────────────────────────────────────────────

  /** Empty arm hit by every size-0 iteration (≥ 1 per run); non-empty by every other. */
  def isEmpty(l: List[Int]): String =
    if (l.isEmpty) "empty" else "non-empty"

  /** Contains-0 on length 5 from `[-5, 5]` is ~38%; on longer lists nearly certain. Both arms
    * comfortable for random.
    */
  def containsZero(l: List[Int]): String =
    if (l.contains(0)) "has-zero" else "no-zero"

  /** Three-arm match on length. The `large` arm fires only late in the schedule once `Gen.listOf`'s
    * size parameter passes 10.
    */
  def sizeClass(l: List[Int]): String = l.size match {
    case 0           => "empty"
    case n if n <= 5 => "small"
    case _           => "large"
  }

  // ── Moderate (~1 – 10%) ──────────────────────────────────────────────────

  /** First element equals last — ~9% at size 2 with 11-symbol alphabet. Vanishing for longer lists.
    * The size guard kills the "empty list trivially matches" loophole.
    */
  def firstIsLast(l: List[Int]): String =
    if (l.size >= 2 && l.head == l.last) "matches" else "no"

  /** All-positive on size ≥ 3 — `(5/11)³ ≈ 9%` at size 3, dropping rapidly with length. */
  def allPositive(l: List[Int]): String =
    if (l.size >= 3 && l.forall(_ > 0)) "all-positive" else "other"

  // ── Hard (~0.1 – 1%) ─────────────────────────────────────────────────────

  /** Sorted on size ≥ 3 — ~21% at size 3 (with repetitions), ~2% at size 5, effectively zero for
    * longer. The size guard is **the** thing distinguishing "random found a sorted list" from
    * "random produced `Nil`."
    */
  def isSorted(l: List[Int]): String =
    if (l.size >= 3 && l == l.sorted) "sorted" else "unsorted"

  /** All-equal on size ≥ 3 — `(1/11)² ≈ 0.8%` at size 3. */
  def allEqual(l: List[Int]): String =
    if (l.size >= 3 && l.distinct.size == 1) "uniform" else "varied"

  /** Palindrome on size ≥ 4 — `(1/11)² ≈ 0.8%` at size 4 (head==last AND second==second-last). */
  def isPalindrome(l: List[Int]): String =
    if (l.size >= 4 && l == l.reverse) "palindrome" else "not"

  /** Sum equals 7 on size ≥ 2 — heavily length-dependent (peaks at short lists, normal-decay for
    * long). The size guard avoids the spurious `Nil.sum == 0` short-circuit.
    */
  def sumIsSeven(l: List[Int]): String =
    if (l.size >= 2 && l.sum == 7) "sums-to-7" else "other"

  /** Contains three consecutive integers somewhere in the list — partial-function branch (4
    * source-level arms: outer `if` × 2, inner partial-function cases × 2). The 11-symbol alphabet
    * makes `a, a+1, a+2` reachable for any non-extreme `a`.
    */
  def consecutiveTriple(l: List[Int]): String = {
    val hit = l.sliding(3).exists {
      case Seq(a, b, c) => b == a + 1 && c == a + 2
      case _            => false
    }
    if (hit) "has-triple" else "no-triple"
  }
}
