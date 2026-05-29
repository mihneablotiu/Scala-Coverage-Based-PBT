package benchmark.list

import benchmark.util.NumberProps

/** `List[Int]`-input benchmark, ordered shallow → deep and grouped by the number of unreached arms random PBT typically leaves behind: saturated, one
  * unreached, two-or-three unreached, four+ unreached.
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

  def allSameSign(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.forall(_ > 0)) "all-positive"
    else if (xs.forall(_ < 0)) "all-negative"
    else "mixed"

  def lengthCompare(xs: List[Int], ys: List[Int]): String =
    if (xs.size > ys.size) "first-longer"
    else if (xs.size < ys.size) "second-longer"
    else "same-length"

  // ── One unreached arm ────────────────────────────────────────────────────

  def sumClass(xs: List[Int]): String =
    if (xs.isEmpty) "empty"
    else if (xs.sum == 0) "zero-sum"
    else if (xs.sum > 0) "positive-sum"
    else "negative-sum"

  def isStrictlySorted(xs: List[Int]): String =
    if (xs.size < 5) "trivial"
    else if (xs.lazyZip(xs.tail).forall(_ < _)) "strict-sorted"
    else "not-strict-sorted"

  // ── Two or three unreached arms ──────────────────────────────────────────

  def allEqual(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.distinct.size == 1) {
      if (xs.head > 0) "all-equal-positive"
      else if (xs.head < 0) "all-equal-negative"
      else "all-zero"
    } else "mixed-values"

  def extremesGap(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else {
      val gap = xs.max.toLong - xs.min.toLong
      if (gap > 1_000_000_000L) "wide"
      else if (gap == 0L) "uniform"
      else if (gap < 1_000_000L) "narrow"
      else "medium-gap"
    }

  def palindromeClass(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs == xs.reverse) "palindrome"
    else if (xs.head == xs.last) "ends-match"
    else "asymmetric"

  def listMaxMin(xs: List[Int]): String =
    if (xs.size < 2) "trivial"
    else if (xs.max == xs.min) "uniform"
    else if (xs.max > 0 && xs.min < 0) {
      if (xs.max == -xs.min) "balanced-around-zero"
      else "asymmetric-mixed-signs"
    } else if (xs.forall(_ >= 0)) "all-non-negative"
    else "all-non-positive"

  def prefixCheck(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size > ys.size) "first-too-long"
    else if (xs == ys.take(xs.size)) {
      if (xs == ys) "identical"
      else "proper-prefix"
    } else "not-prefix"

  def isReverseOf(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs == ys.reverse) "is-reverse"
    else "unrelated"

  def haveSameMultiset(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size != ys.size) "different-size"
    else if (xs.sorted == ys.sorted) "same-multiset"
    else "different-multiset"

  // ── Four+ unreached arms ─────────────────────────────────────────────────

  def allPrime(xs: List[Int]): String =
    if (xs.size < 3) "tiny"
    else if (xs.forall(NumberProps.isPrime)) {
      if (xs == xs.sorted) "ascending-primes"
      else "unsorted-primes"
    } else if (xs.count(NumberProps.isPrime) >= 2) "multiple-primes"
    else if (xs.exists(NumberProps.isPrime)) "single-prime"
    else "no-primes"

  def primeListShape(xs: List[Int]): String =
    if (xs.size < 3) "tiny"
    else if (xs.forall(_ > 0)) {
      if (xs.forall(NumberProps.isPrime)) {
        if (xs == xs.sorted) "sorted-prime-positives"
        else "unsorted-prime-positives"
      } else if (xs.exists(NumberProps.isPrime)) "positives-with-some-prime"
      else "positives-no-prime"
    } else "non-positive-or-mixed"

  def isPermutation(xs: List[Int]): String = {
    val n = xs.size
    if (n == 0) "empty"
    else if (xs.distinct.size != n) "has-duplicates"
    else if (xs.exists(x => x < 1 || x > n)) "out-of-range"
    else if (xs == (1 to n).toList) "identity-permutation"
    else if (xs == (1 to n).toList.reverse) "reverse-permutation"
    else "other-valid-permutation"
  }

  def deepListRelation(xs: List[Int], ys: List[Int]): String =
    if (xs.isEmpty || ys.isEmpty) "trivial"
    else if (xs.size == ys.size) {
      if (xs == ys) "identical"
      else if (xs == ys.reverse) "reverse-of"
      else if (xs.sorted == ys.sorted) "same-multiset"
      else if (xs.head == ys.head) "head-match"
      else "different-content"
    } else "different-sizes"

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
