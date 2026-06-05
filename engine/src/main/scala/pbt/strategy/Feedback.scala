package pbt.strategy

final case class Feedback[A](covered: Set[Int], corpus: Vector[A], history: Vector[Set[Int]], events: Vector[Feedback.Event]) {

  def iteration: Int = history.size

  def firstHits: Map[Int, Int] =
    history.iterator.zipWithIndex.flatMap { case (delta, i) => delta.iterator.map(_ -> i) }.toMap

  def record(input: A, nowCovered: Set[Int]): Feedback[A] = {
    val delta       = nowCovered -- covered
    val nextCovered = covered ++ delta
    val nextCorpus  = if (delta.nonEmpty) corpus :+ input else corpus
    val event       =
      Option.when(delta.nonEmpty)(
        Feedback.Event(
          iteration = iteration,
          input = input.toString.take(240),
          newStatements = delta,
          coveredTotal = nextCovered.size,
          corpusSize = nextCorpus.size
        )
      )
    Feedback(nextCovered, nextCorpus, history :+ delta, events ++ event)
  }
}

object Feedback {
  final case class Event(iteration: Int, input: String, newStatements: Set[Int], coveredTotal: Int, corpusSize: Int)

  def empty[A]: Feedback[A] = Feedback(Set.empty, Vector.empty, Vector.empty, Vector.empty)
}
