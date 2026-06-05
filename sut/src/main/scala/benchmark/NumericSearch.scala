package benchmark

object NumericSearch {

  def window(n: Int): String =
    if (n > 499995 && n < 500005) "hit" else "miss"

  def derivedEq(n: Int): String =
    if (2 * n == 1000000) "hit" else "miss"

  def offsetEq(n: Int): String =
    if (n + 7 == 1000000) "hit" else "miss"

  def band(x: Int, y: Int): String =
    if (x > 1000 && x < 1002 && y > 2000 && y < 2002) "hit" else "miss"

  def scaledOffset(n: Int): String =
    if (3 * n + 7 == 1000000) "hit" else "miss"
}
