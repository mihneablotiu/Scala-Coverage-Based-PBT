package benchmark.list

/** `List[Int]`-input benchmark. Ten methods split between "easy" branches (random covers both) and
  * "hard" structural branches (random rarely or never produces the structure required).
  *
  * The hard category is where random PBT's blind spot is most visible: properties like "the list is
  * sorted" are extremely unlikely to hold for a list of uniform random integers.
  */
object ListBench {

  // ── easy: both arms commonly populated by `Gen.listOf` ─────────────

  def emptiness(l: List[Int]): String =
    if (l.isEmpty) "empty" else "non-empty"

  def hasPositive(l: List[Int]): String =
    if (l.exists(_ > 0)) "has-positive" else "no-positive"

  def lengthParity(l: List[Int]): String =
    if (l.length % 2 == 0) "even-length" else "odd-length"

  // ── hard: structural conditions ────────────────────────────────────

  /** A random list is virtually never sorted. */
  def isSorted(l: List[Int]): String =
    if (l == l.sorted) "sorted" else "unsorted"

  /** Distinctness depends on length: short lists are usually distinct, long ones usually aren't. */
  def isDistinct(l: List[Int]): String =
    if (l.distinct.length == l.length) "distinct" else "duplicate"

  /** A random list is virtually never a palindrome. */
  def isPalindrome(l: List[Int]): String =
    if (l == l.reverse) "palindrome" else "not"

  /** Probability a list contains a specific `Int` is tiny under uniform `Int` generation. */
  def containsAnswer(l: List[Int]): String =
    if (l.contains(42)) "has-answer" else "no-answer"

  /** Requires every element strictly positive — vanishingly rare for moderate-length lists. */
  def allPositive(l: List[Int]): String =
    if (l.nonEmpty && l.forall(_ > 0)) "all-positive" else "other"

  /** First element must be exactly 0 — chance ≈ 1 in 2³² when the list is non-empty. */
  def firstIsZero(l: List[Int]): String =
    if (l.headOption.contains(0)) "first-zero" else "other"

  /** Three consecutive integers somewhere in the list. */
  def consecutiveTriple(l: List[Int]): String = {
    val hit = l.sliding(3).exists {
      case Seq(a, b, c) => b == a + 1 && c == a + 2
      case _            => false
    }
    if (hit) "has-triple" else "no-triple"
  }
}
