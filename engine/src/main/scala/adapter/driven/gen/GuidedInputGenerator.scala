package adapter.driven.gen

import cats.effect.IO
import domain.SessionFeedback
import port.driven.InputGenerator

/** Placeholder coverage-guided generator.
  *
  * It receives the same [[SessionFeedback]] a real guided strategy would — cumulative line
  * coverage, hit counts, first-hit indices, growth curve, full input history — and prints a compact
  * summary so the channel is visibly working. It then delegates to a fallback (typically
  * [[RandomInputGenerator]]) for the actual `Int` value.
  *
  * Replacing the `printFeedback` body with a real selection/mutation algorithm is the next step in
  * the project.
  */
object GuidedInputGenerator {

  def apply(fallback: InputGenerator): InputGenerator = new InputGenerator {

    override def next(feedback: SessionFeedback): IO[Int] = for {
      _ <- IO(printFeedback(feedback))
      value <- fallback.next(feedback)
    } yield value

    private def printFeedback(f: SessionFeedback): Unit = {
      val totalCovered = f.cumulativeCoverage.values.iterator.map(_.covered).sum
      val totalBranch = f.cumulativeCoverage.values.iterator.map(_.total).sum
      val lastInput = f.history.lastOption.fold("—")(r => r.input.toString)
      println(
        f"[guided] iter=${f.iteration}%-3d coverage=$totalCovered/$totalBranch  lines=${f.cumulativeCoverage.size}%-2d  lastInput=$lastInput"
      )
    }
  }
}
