package pbt.strategy

final case class Feedback[A](
    covered: Set[Int],
    corpus: Vector[A],
    history: Vector[Set[Int]],
    events: Vector[Feedback.Event],
    traces: Vector[Feedback.Trace]
) {

  def iteration: Int = history.size

  def firstHits: Map[Int, Int] =
    history.iterator.zipWithIndex.flatMap { case (delta, i) => delta.iterator.map(_ -> i) }.toMap

  def record(candidate: Tactic.Candidate[A], nowCovered: Set[Int]): Feedback[A] = {
    val delta       = nowCovered -- covered
    val nextCovered = covered ++ delta
    val nextCorpus  = if (delta.nonEmpty) corpus :+ candidate.input else corpus
    val before      = Feedback.snapshot(covered, corpus)
    val after       = Feedback.snapshot(nextCovered, nextCorpus)
    val event       =
      Option.when(delta.nonEmpty)(
        Feedback.Event(
          iteration = iteration,
          input = Feedback.show(candidate.input),
          newStatements = delta,
          coveredTotal = nextCovered.size,
          corpusSize = nextCorpus.size
        )
      )
    val trace = Feedback.Trace(
      iteration = iteration,
      source = candidate.source,
      availableSources = candidate.availableSources,
      input = Feedback.show(candidate.input),
      feedbackBefore = before,
      coveredNow = nowCovered,
      newStatements = delta,
      feedbackAfter = after
    )
    Feedback(nextCovered, nextCorpus, history :+ delta, events ++ event, traces :+ trace)
  }
}

object Feedback {
  final case class Event(iteration: Int, input: String, newStatements: Set[Int], coveredTotal: Int, corpusSize: Int)
  final case class Snapshot(coveredStatements: Set[Int], coveredTotal: Int, corpusSize: Int, lastCorpusInput: Option[String])
  final case class Trace(
      iteration: Int,
      source: String,
      availableSources: List[String],
      input: String,
      feedbackBefore: Snapshot,
      coveredNow: Set[Int],
      newStatements: Set[Int],
      feedbackAfter: Snapshot
  )

  def empty[A]: Feedback[A] = Feedback(Set.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  private def snapshot[A](covered: Set[Int], corpus: Vector[A]): Snapshot =
    Snapshot(
      coveredStatements = covered,
      coveredTotal = covered.size,
      corpusSize = corpus.size,
      lastCorpusInput = corpus.lastOption.map(show)
    )

  private def show[A](value: A): String =
    value.toString.take(240)
}
