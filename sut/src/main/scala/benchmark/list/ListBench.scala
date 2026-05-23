package benchmark.list

/** `List[Int]`-input benchmark — increasing difficulty across the file.
  *
  * Under default `Arbitrary[List[Int]]` the size grows from 0 to 100 across the run; each element
  * is drawn from `Arbitrary[Int]` (full 32-bit range plus boundary specials `0`, `1`, `-1`,
  * `MinValue`, `MaxValue`). The structurally hard cases for random PBT are:
  *
  *   - **Value coincidences** between independent positions (`head == last`, `xs == xs.reverse`,
  *     `xs == xs.sorted`).
  *   - **Multi-list relationships** (`xs == ys.reverse`, `xs.sorted == ys.sorted`).
  *   - **Properties at non-trivial sizes** — sortedness, strict sortedness, palindromicity, all
  *     requiring `size ≥ K` guards so the trivial small-size cases don't dominate.
  *
  * Sections, top to bottom: trivial → easy → moderate → list properties → multi-list.
  */
object ListBench {

  // ── Trivial: random saturates ────────────────────────────────────────────

  def isEmpty(xs: List[Int]): String =
    if (xs.isEmpty) "empty" else "non-empty"

  def sizeClass(xs: List[Int]): String = xs.size match {
    case 0            => "empty"
    case n if n <= 5  => "small"
    case n if n <= 20 => "medium"
    case _            => "large"
  }

  // ── Easy: structural classifiers with boundary-reachable arms ────────────

  /** Four arms — `zero-head` reachable via boundary special `0` in the head position. */
  def firstClass(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.head > 0) "positive-head"
    else if (xs.head < 0) "negative-head"
    else "zero-head"

  /** Four arms; small lists give all-positive / all-negative cheaply (size 1 already does it). */
  def allSameSign(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.forall(_ > 0)) "all-positive"
    else if (xs.forall(_ < 0)) "all-negative"
    else "mixed"

  // ── Moderate: value coincidences and sum-based properties ────────────────

  /** Four arms; `zero-sum` (`xs.sum == 0`) needs a value coincidence under full `Int` —
    * effectively unreachable.
    */
  def sumClass(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.sum == 0) "zero-sum"
    else if (xs.sum > 0) "positive-sum"
    else "negative-sum"

  /** Five arms. `ends-equal` (head == last) needs a value coincidence between two list
    * positions; possible via repeated boundary specials, vanishing otherwise.
    */
  def headTailRelation(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.size == 1) "singleton"
    else if (xs.head == xs.last) "ends-equal"
    else if (xs.head > xs.last) "head-bigger"
    else "tail-bigger"

  /** Four arms; `narrow` and `uniform` need every element to fall inside a tight range.
    * `uniform` (`max == min` for `size ≥ 2`) is effectively unreachable.
    */
  def extremesGap(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.max.toLong - xs.min.toLong > 1_000_000_000L) "wide"
    else if (xs.max.toLong - xs.min.toLong > 0L) "narrow"
    else "uniform"

  // ── Hard: list properties at non-trivial sizes ───────────────────────────

  /** Three arms; `size < 2` skips the vacuous case so the `sorted` arm reflects an actual
    * ordering between two distinct positions. Reachable for short sizes (P = 0.5 at size 2),
    * vanishing as size grows.
    */
  def isSorted(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs == xs.sorted) "sorted"
    else "unsorted"

  /** Three arms; requires `size ≥ 5` so short-list near-misses don't dominate. Strictly
    * increasing under uniform `Int` has P ≈ 1/n! averaged across the schedule — borderline at
    * best.
    */
  def isStrictlySorted(xs: List[Int]): String =
    if (xs.size < 5) "trivial"
    else if (xs.lazyZip(xs.tail).forall(_ < _)) "strict-sorted"
    else "not-strict-sorted"

  /** Three arms; `size >= 2` skips the vacuous case. `all-equal` needs every element identical
    * — vanishing under full `Int`.
    */
  def allEqual(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.distinct.size == 1) "all-equal"
    else "mixed-values"

  /** Four arms. Both `palindrome` (`xs == xs.reverse`) and `ends-match` (`head == last`) need
    * mirrored-position value coincidences — effectively unreachable for `size ≥ 2` under full
    * `Int`.
    */
  def palindromeClass(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs == xs.reverse) "palindrome"
    else if (xs.head == xs.last) "ends-match"
    else "asymmetric"

  // ── Multi-list relationships (structural, no literals) ───────────────────

  /** Three arms over two lists; all reachable via the size schedule. */
  def lengthCompare(xs: List[Int], ys: List[Int]): String =
    if (xs.size > ys.size) "first-longer"
    else if (xs.size < ys.size) "second-longer"
    else "same-length"

  /** Four arms; `is-reverse` (`xs == ys.reverse`) needs every position-pair to coincide between
    * independent draws — effectively unreachable for non-trivial sizes.
    */
  def isReverseOf(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs == ys.reverse) "is-reverse"
    else "unrelated"

  /** Four arms; `same-multiset` (`xs.sorted == ys.sorted`) needs the two lists to be
    * permutations of each other — vanishing under independent draws over full `Int`.
    */
  def haveSameMultiset(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs.sorted == ys.sorted) "same-multiset"
    else "different-multiset"

  /** Four arms over a list and a target. `head-match` is reachable via boundary specials
    * (target == 0 and xs.head == 0); `interior-match` (target somewhere other than the head)
    * needs the target to coincide with a list element — vanishing under independent draws.
    */
  def findTarget(xs: List[Int], target: Int): String =
    if (xs.isEmpty) "empty-list"
    else if (xs.head == target) "head-match"
    else if (xs.tail.contains(target)) "interior-match"
    else "not-found"

  // ── Deeply nested: leaves compound 3-4 structural filters in sequence ────

  /** Single-list deeply nested classification. Filters by size (≥ 5 only), then by uniform
    * sign across all elements, then by sortedness, then by distinctness. Under random
    * `List[Int]`, getting a size-5+ list whose every element is positive is already ~3% per
    * input; getting that *and* it being sorted is another 1/n! on top. The leaves inside the
    * "all-positive sorted" sub-tree, and the entire "all-negative sorted" sub-tree, are
    * effectively unreachable.
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

  /** Two-list deeply nested relationship. Filters by emptiness, then by size equality, then
    * by full-content match, reverse match, multiset match, and finally a head-match check.
    * The inner content arms (`identical`, `reverse-of`, `same-multiset`) all require
    * position-by-position value coincidences between independent draws — unreachable under
    * full `Int` for non-trivial sizes.
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
}
