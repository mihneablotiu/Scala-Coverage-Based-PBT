package pbt.strategy

/** The live state of a run, grown one input at a time by [[record]]:
  *   - `covered` — the leaf positions covered so far (pooling stops once nothing is left to cover);
  *   - `corpus` — the inputs that each first covered a new leaf (mutation perturbs these);
  *   - `history` — per input, the leaves it first covered (gives each leaf its first-hit input index, for the report).
  */
final case class Feedback[A](covered: Set[Int], corpus: Vector[A], history: Vector[Set[Int]]) {

  def iteration: Int = history.size

  /** Leaf position → index of the input that first covered it. */
  def firstHits: Map[Int, Int] =
    history.iterator.zipWithIndex.flatMap { case (delta, i) => delta.iterator.map(_ -> i) }.toMap

  /** Fold in one input's coverage: the delta is the newly-covered leaves; the input joins the corpus iff it covered something new. */
  def record(input: A, nowCovered: Set[Int]): Feedback[A] = {
    val delta = nowCovered -- covered
    Feedback(covered ++ delta, if (delta.nonEmpty) corpus :+ input else corpus, history :+ delta)
  }
}

object Feedback {
  def empty[A]: Feedback[A] = Feedback(Set.empty, Vector.empty, Vector.empty)
}
