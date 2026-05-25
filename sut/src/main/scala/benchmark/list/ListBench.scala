package benchmark.list

import benchmark.util.NumberProps

/** `List[Int]`-input benchmark — ordered shallow → deep, grouped by the number of unreached arms
  * random PBT leaves behind.
  *
  * Sections:
  *   - **Saturated** (every arm reached) — trivial baselines.
  *   - **One unreached arm** — single structural rare condition.
  *   - **Two or three unreached arms** — rare conditions split across distinct sub-trees.
  *   - **Four+ unreached arms** — deep nested classification and algorithm-driven shape checks.
  */
object ListBench {

  // ── Saturated ────────────────────────────────────────────────────────────

  def isEmpty(xs: List[Int]): String =
    if (xs.isEmpty) "empty" else "non-empty"

  def sizeClass(xs: List[Int]): String = xs.size match {
    case 0            => "empty"
    case n if n <= 5  => "small"
    case n if n <= 20 => "medium"
    case _            => "large"
  }

  /** Four arms — `all-positive` / `all-negative` reach via single-element lists, `mixed` is the
    * common case, `empty` via the size schedule.
    */
  def allSameSign(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.forall(_ > 0)) "all-positive"
    else if (xs.forall(_ < 0)) "all-negative"
    else "mixed"

  /** Three-arm length comparison over two lists, all reachable via the size schedule. */
  def lengthCompare(xs: List[Int], ys: List[Int]): String =
    if (xs.size > ys.size) "first-longer"
    else if (xs.size < ys.size) "second-longer"
    else "same-length"

  // ── One unreached arm ────────────────────────────────────────────────────

  /** Four arms; `zero-sum` is the one unreachable. */
  def sumClass(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.sum == 0) "zero-sum"
    else if (xs.sum > 0) "positive-sum"
    else "negative-sum"

  /** Three arms — `size ≥ 5` strict ordering is ~1/n! averaged. */
  def isStrictlySorted(xs: List[Int]): String =
    if (xs.size < 5) "trivial"
    else if (xs.lazyZip(xs.tail).forall(_ < _)) "strict-sorted"
    else "not-strict-sorted"

  // ── Two or three unreached arms, spread across sub-trees ─────────────────

  /** Five arms. After the `all-equal` filter (very rare for full `Int`), the inner sign split gives
    * `all-equal-positive` / `all-equal-negative` (both unreachable), and `all-zero` (which boundary
    * `0`-repeats can hit). The unreached arms are in two distinct sub-arms of the all-equal branch.
    */
  def allEqual(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.distinct.size == 1) {
      if (xs.head > 0) "all-equal-positive"
      else if (xs.head < 0) "all-equal-negative"
      else "all-zero"
    } else "mixed-values"

  /** Five arms. `narrow` / `uniform` split inside the medium gap — `uniform` is unreachable (value
    * coincidence), `narrow` is unreachable (small spread of full-range elements).
    */
  def extremesGap(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else {
      val gap = xs.max.toLong - xs.min.toLong
      if (gap > 1_000_000_000L) "wide"
      else if (gap == 0L) "uniform"
      else if (gap < 1_000_000L) "narrow"
      else "medium-gap"
    }

  /** Four arms. `palindrome` and `ends-match` both need mirrored-position value coincidences — both
    * unreachable; the two unreached arms sit at different depths of the conditional chain.
    */
  def palindromeClass(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs == xs.reverse) "palindrome"
    else if (xs.head == xs.last) "ends-match"
    else "asymmetric"

  /** Six arms. After the size-≥-2 gate, the `uniform` (`max == min`) and `balanced-around-zero`
    * (`max == -min`) arms are value-coincidence arms in distinct sub-trees of the max/min check;
    * `all-non-negative` and `all-non-positive` are reachable for short lists.
    */
  def listMaxMin(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.max == xs.min) "uniform"
    else if (xs.max > 0 && xs.min < 0) {
      if (xs.max == -xs.min) "balanced-around-zero"
      else "asymmetric-mixed-signs"
    } else if (xs.forall(_ >= 0)) "all-non-negative"
    else "all-non-positive"

  /** Five arms. Inside the prefix gate, `identical` and `proper-prefix` both require
    * position-by-position coincidence between independent draws — both unreached.
    */
  def prefixCheck(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size > ys.size) "first-too-long"
    else if (xs == ys.take(xs.size)) {
      if (xs == ys) "identical"
      else "proper-prefix"
    } else "not-prefix"

  /** Four arms; `is-reverse` requires full position-by-position coincidence. */
  def isReverseOf(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs == ys.reverse) "is-reverse"
    else "unrelated"

  /** Four arms; `same-multiset` requires permutation equivalence under independent draws. */
  def haveSameMultiset(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs.sorted == ys.sorted) "same-multiset"
    else "different-multiset"

  // ── Four+ unreached arms — deep / algorithm-driven ───────────────────────

  /** Five arms via nested guards on primality count. `all-primes` (and its sortedness split) is
    * unreachable; `multiple-primes` is reachable but rare.
    */
  def allPrime(xs: List[Int]): String =
    if (xs.size < 3) "tiny"
    else if (xs.forall(NumberProps.isPrime)) {
      if (xs == xs.sorted) "ascending-primes"
      else "unsorted-primes"
    } else if (xs.count(NumberProps.isPrime) >= 2) "multiple-primes"
    else if (xs.exists(NumberProps.isPrime)) "single-prime"
    else "no-primes"

  /** Six outcomes, deeper nesting: size ≥ 3 → uniform-positive → all-prime → sorted. Compound
    * conditions (size + positive + prime + sorted) make the deep arms unreachable; the unreached
    * arms span the all-positive sub-tree.
    */
  def primeListShape(xs: List[Int]): String =
    if (xs.size < 3) "tiny"
    else if (xs.forall(_ > 0)) {
      if (xs.forall(NumberProps.isPrime)) {
        if (xs == xs.sorted) "sorted-prime-positives"
        else "unsorted-prime-positives"
      } else if (xs.exists(NumberProps.isPrime)) "positives-with-some-prime"
      else "positives-no-prime"
    } else "non-positive-or-mixed"

  /** Algorithm-driven: is `xs` a permutation of `[1..n]` for `n = xs.size`? Random `List[Int]`
    * essentially never satisfies this; the unreached arms cover *three distinct identity-style
    * permutations* — identity, reverse, and any other valid permutation — each a separate leaf.
    * Plus the in-range check sub-tree fires only for an all-`[1..n]` list, which is itself
    * unreachable.
    */
  def isPermutation(xs: List[Int]): String = {
    val n = xs.size
    if (n == 0) "empty"
    else if (xs.distinct.size != n) "has-duplicates"
    else if (xs.exists(x => x < 1 || x > n)) "out-of-range"
    else if (xs == (1 to n).toList) "identity-permutation"
    else if (xs == (1 to n).toList.reverse) "reverse-permutation"
    else "other-valid-permutation"
  }

  /** Two-list deeply nested relationship — non-empty / size match / full content / reverse /
    * multiset / head match. Three structural-coincidence arms are unreachable (`identical`,
    * `reverse-of`, `same-multiset`); `head-match` is boundary-reachable.
    */
  def deepListRelation(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size == ys.size) {
      if (xs == ys) "identical"
      else if (xs == ys.reverse) "reverse-of"
      else if (xs.sorted == ys.sorted) "same-multiset"
      else if (xs.head == ys.head) "head-match"
      else "different-content"
    } else "different-sizes"

  /** Deepest single-list classification — size ≥ 5 / uniform sign / sortedness / distinctness. The
    * all-positive *and* all-negative sub-trees are both unreached (random rarely produces size-5
    * lists with uniform sign); their sortedness leaves contribute 7+ unreached branches spread
    * across two distinct sub-trees of the outer if.
    */
  def deepListShape(xs: List[Int]): String =
    if (xs.size < 5) "tiny"
    else if (xs.forall(_ > 0)) {
      if (xs == xs.sorted) {
        if (xs.distinct.size == xs.size) "strictly-ascending-positive"
        else "ascending-with-duplicates-positive"
      } else if (xs == xs.sorted.reverse) "descending-positive"
      else "unsorted-positive"
    } else if (xs.forall(_ < 0)) {
      if (xs == xs.sorted) "ascending-negative"
      else "unsorted-negative"
    } else "mixed-signs"
}
