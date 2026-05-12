package branchhard

object HardToReachBranches {

  def classify97(n: Int): String = if (n % 97 == 13) "lucky" else "normal"

  def isPositive(n: Int): String = if (n > 0) "positive" else "non-positive"

  def category(n: Int): String = {
    if (n > 100) {
      if (n % 7 == 0) "big-lucky" else "big"
    } else if (n == 42) "answer"
    else if (n > 0) "small"
    else "non-positive"
  }
}
