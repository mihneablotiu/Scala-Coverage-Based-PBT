package pbt.strategy

import pbt.analysis.Pos

/** The live state of a run, grown one input at a time by [[record]]. It is the single signal every tactic reads, and the report is built from it.
  *   - `covered` — the cumulative set of covered leaves; the pool and gradient read this to know what's still missing.
  *   - `corpus` — the inputs that each first covered a new leaf; the mutation tactic perturbs these.
  *   - `history` — per input, the leaves it *first* covered; drives the growth curve and per-leaf first-hit in the report.
  */
final case class Feedback[A](covered: Set[Pos], corpus: Vector[A], history: Vector[Set[Pos]]) {

  def iteration: Int = history.size

  /** Cumulative covered-leaf count after each input. */
  def growthCurve: Vector[Int] = history.scanLeft(0)(_ + _.size).tail

  /** Leaf position → index of the input that first covered it. */
  def firstHits: Map[Pos, Int] = history.iterator.zipWithIndex.flatMap { case (delta, i) => delta.iterator.map(_ -> i) }.toMap

  /** Fold in one input's cumulative coverage: the new leaves are the delta vs `covered`; the input joins the corpus iff it covered something new. */
  def record(input: A, nowCovered: Set[Pos]): Feedback[A] = {
    val delta = nowCovered -- covered
    Feedback(covered ++ delta, if (delta.nonEmpty) corpus :+ input else corpus, history :+ delta)
  }
}

object Feedback {
  def empty[A]: Feedback[A] = Feedback(Set.empty, Vector.empty, Vector.empty)
}
