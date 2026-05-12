package domain

final case class BranchCounter(covered: Int, total: Int) {
  def isFullyCovered: Boolean = total > 0 && covered == total
  def isPartiallyCovered: Boolean = covered > 0 && covered < total
  def isNotCovered: Boolean = covered == 0
  def +(other: BranchCounter): BranchCounter =
    BranchCounter(covered + other.covered, total + other.total)
}

object BranchCounter {
  val Zero: BranchCounter = BranchCounter(0, 0)
}
