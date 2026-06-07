package pbt.strategy

import pbt.targeting.{BestAttempt, BranchDistance, BranchGoal, OptionalNumericFields}

final case class Feedback[A](
    iteration: Int,
    coveredAt: Map[Int, Int],
    corpus: Vector[A],
    seenInputs: Set[A],
    targeted: Map[Int, BestAttempt[A]]
) {

  def record(input: A, nowCovered: Set[Int], targetGoals: List[BranchGoal] = Nil)(implicit fields: OptionalNumericFields[A]): Feedback[A] = {
    val delta         = nowCovered -- coveredAt.keySet
    val nextCoveredAt = coveredAt ++ delta.map(_ -> iteration)
    val nextCorpus    = if (delta.nonEmpty) corpus :+ input else corpus
    val distances     = BranchDistance.distances(targetGoals, input)
    val nextTargeted  = distances.foldLeft(targeted) { case (best, (goalId, distance)) =>
      best.get(goalId) match {
        case Some(previous) if previous.distance <= distance => best
        case _                                               => best.updated(goalId, BestAttempt(input, distance, iteration))
      }
    }
    Feedback(iteration + 1, nextCoveredAt, nextCorpus, seenInputs + input, nextTargeted)
  }
}

object Feedback {
  def empty[A]: Feedback[A] = Feedback(0, Map.empty, Vector.empty, Set.empty, Map.empty)
}
