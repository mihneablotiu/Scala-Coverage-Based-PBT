package pbt.strategy

final case class Feedback[A](
    iteration: Int,
    coveredAt: Map[Int, Int],
    corpus: Vector[A],
    seenInputs: Set[A]
) {

  def record(input: A, nowCovered: Set[Int]): Feedback[A] = {
    val delta         = nowCovered -- coveredAt.keySet
    val nextCoveredAt = coveredAt ++ delta.map(_ -> iteration)
    val nextCorpus    = if (delta.nonEmpty) corpus :+ input else corpus
    Feedback(iteration + 1, nextCoveredAt, nextCorpus, seenInputs + input)
  }
}

object Feedback {
  def empty[A]: Feedback[A] = Feedback(0, Map.empty, Vector.empty, Set.empty)
}
