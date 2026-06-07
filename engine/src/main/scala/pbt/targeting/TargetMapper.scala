package pbt.targeting

import pbt.Coverage

object TargetMapper {

  def goals(targets: List[Coverage.StatementTarget], predicates: List[ExtractedPredicate]): List[BranchGoal] = {
    val branchTargets = targets.filter(_.branch)

    predicates
      .flatMap(predicate => goalsFor(branchTargets, predicate))
      .zipWithIndex
      .map { case ((coverageId, conditions), id) =>
        BranchGoal(id, coverageId, conditions)
      }
  }

  private def goalsFor(targets: List[Coverage.StatementTarget], predicate: ExtractedPredicate): List[(Int, List[BranchCondition])] =
    List(
      goalIn(targets, predicate.trueStart, predicate.trueEnd, predicate.trueConditions),
      goalIn(targets, predicate.falseStart, predicate.falseEnd, predicate.falseConditions)
    ).flatten

  private def goalIn(
      targets: List[Coverage.StatementTarget],
      start: Int,
      end: Int,
      conditions: List[BranchCondition]
  ): Option[(Int, List[BranchCondition])] =
    targets
      .filter(overlaps(_, start, end))
      .minByOption(target => targetRank(target, start, end))
      .map(target => (target.id, conditions))

  private def targetRank(target: Coverage.StatementTarget, start: Int, end: Int): (Int, Int, Int, Int) = {
    val relation =
      if (target.start == start && target.end == end) 0
      else if (start <= target.start && target.end <= end) 1
      else 2

    (relation, target.end - target.start, target.start, target.id)
  }

  private def overlaps(target: Coverage.StatementTarget, start: Int, end: Int): Boolean =
    target.start <= end && start <= target.end
}
