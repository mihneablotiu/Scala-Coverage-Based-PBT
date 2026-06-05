package pbt.strategy

final case class Feedback[A](covered: Set[Int], corpus: Vector[A], history: Vector[Set[Int]]) {

  def iteration: Int = history.size

  def firstHits: Map[Int, Int] =
    history.iterator.zipWithIndex.flatMap { case (delta, i) => delta.iterator.map(_ -> i) }.toMap

  def record(input: A, nowCovered: Set[Int]): Feedback[A] = {
    val delta = nowCovered -- covered
    Feedback(covered ++ delta, if (delta.nonEmpty) corpus :+ input else corpus, history :+ delta)
  }
}

object Feedback {
  def empty[A]: Feedback[A] = Feedback(Set.empty, Vector.empty, Vector.empty)
}
