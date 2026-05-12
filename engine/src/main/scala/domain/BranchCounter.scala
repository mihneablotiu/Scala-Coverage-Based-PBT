package domain

final case class BranchCounter(covered: Int, total: Int) {
  def isFullyCovered: Boolean = total > 0 && covered == total
  def isPartiallyCovered: Boolean = covered > 0 && covered < total
  def isNotCovered: Boolean = covered == 0
}
