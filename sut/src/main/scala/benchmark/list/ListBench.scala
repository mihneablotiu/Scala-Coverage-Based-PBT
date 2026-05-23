package benchmark.list

import benchmark.util.NumberProps

/** `List[Int]`-input benchmark — structural-rarity gradient, ordered shallow → deep.
  *
  * Under default `Arbitrary[List[Int]]` the size grows from 0 to 100 across the run; each element
  * is full-range `Int` plus boundary specials. The structurally hard cases for random PBT are:
  *
  *   - **Position coincidences** (`head == last`, `xs == xs.reverse`, `xs.sum == 0`).
  *   - **Multi-list relationships** (`xs == ys.reverse`, `xs.sorted == ys.sorted`).
  *   - **Properties at non-trivial sizes** — sortedness/palindromicity with `size ≥ K` guards.
  *   - **Element-wise number-theoretic properties** (all-prime, sorted-prime-positives).
  *
  * Sections: trivial → moderate → list properties → multi-list relationships → primality →
  * deepest compound trees.
  */
object ListBench {

  // ── Trivial baselines ────────────────────────────────────────────────────

  def isEmpty(xs: List[Int]): String =
    if (xs.isEmpty) "empty" else "non-empty"

  def sizeClass(xs: List[Int]): String = xs.size match {
    case 0            => "empty"
    case n if n <= 5  => "small"
    case n if n <= 20 => "medium"
    case _            => "large"
  }

  // ── Moderate: value coincidences and aggregation ─────────────────────────

  /** Four arms; `zero-sum` (`xs.sum == 0`) needs a value coincidence under full `Int` —
    * effectively unreachable.
    */
  def sumClass(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.sum == 0) "zero-sum"
    else if (xs.sum > 0) "positive-sum"
    else "negative-sum"

  /** Four arms; `narrow` needs the spread to be inside a 10⁹ window. `uniform` (`max == min`
    * for `size ≥ 2`) is effectively unreachable.
    */
  def extremesGap(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.max.toLong - xs.min.toLong > 1_000_000_000L) "wide"
    else if (xs.max.toLong - xs.min.toLong > 0L) "narrow"
    else "uniform"

  // ── List shape properties ────────────────────────────────────────────────

  /** Three arms; `size ≥ 2` skips the vacuous case. `all-equal` needs every element identical —
    * vanishing under full `Int`.
    */
  def allEqual(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.distinct.size == 1) "all-equal"
    else "mixed-values"

  /** Three arms; `size ≥ 5` strict ordering is ~1/n! averaged across the schedule. */
  def isStrictlySorted(xs: List[Int]): String =
    if (xs.size < 5) "trivial"
    else if (xs.lazyZip(xs.tail).forall(_ < _)) "strict-sorted"
    else "not-strict-sorted"

  /** Four arms. `palindrome` and `ends-match` both need mirrored-position value coincidences. */
  def palindromeClass(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs == xs.reverse) "palindrome"
    else if (xs.head == xs.last) "ends-match"
    else "asymmetric"

  // ── Multi-list relationships ─────────────────────────────────────────────

  /** Four arms; `is-reverse` needs full position-by-position coincidence with the reverse of an
    * independent draw — unreachable.
    */
  def isReverseOf(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs == ys.reverse) "is-reverse"
    else "unrelated"

  /** Four arms; `same-multiset` needs the two lists to be permutations of each other under
    * independent draws — vanishing.
    */
  def haveSameMultiset(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs.sorted == ys.sorted) "same-multiset"
    else "different-multiset"

  // ── Element-wise primality (medium-hard) ─────────────────────────────────

  /** Five arms via nested guards. `all-prime` (every element prime, size ≥ 3) is ~0.05ⁿ —
    * unreachable. `multiple-primes` and `single-prime` are reachable but rare (`MaxValue` is
    * itself the Mersenne prime, so chooseNum's bias toward boundary specials occasionally pulls
    * in a prime).
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
    * conditions (size + positive + prime + sorted) make the deep arms unreachable.
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

  // ── Hard: deepest compound trees ─────────────────────────────────────────

  /** Two-list deeply nested relationship. Non-empty → size match → full-content / reverse /
    * multiset / head match. The first three inner arms all require position-by-position value
    * coincidences between independent draws — unreachable.
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

  /** Deepest single-list tree: size ≥ 5 → uniform sign → sortedness → distinctness. The all-
    * positive and all-negative sub-trees are reached very rarely (small sizes only); their
    * sortedness leaves are unreachable.
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
