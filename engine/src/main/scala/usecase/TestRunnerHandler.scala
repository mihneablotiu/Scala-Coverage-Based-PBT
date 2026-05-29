package usecase

import cats.effect.IO
import domain.{Pos, SessionFeedback, SessionReport, Strategy}
import org.scalacheck.{Gen, Prop, Test}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}

import java.nio.file.Path
import scala.util.Try

/** One fuzz session against one method.
  *
  * Three steps: parse the method (tree + leaf positions in one shot), drive ScalaCheck through `Gen.delay(strategy.gen(feedback))` so each iteration
  * re-consults the strategy with fresh feedback, hand the resulting [[SessionReport]] to the writer.
  *
  * The property is always evaluated (`Try(property(input))` so a thrown predicate doesn't abort the loop) but its boolean result is currently
  * discarded — `Prop.forAll` always sees `true`, so coverage measurement keeps going regardless of the predicate's outcome. The `var feedback` is
  * contained to `runScalaCheck`.
  */
final class TestRunnerHandler(
    treeBuilder: BranchTreeBuilder,
    sourceCoverage: SourceCoverageReader,
    writer: CoverageReportWriter,
    params: Test.Parameters
) {

  def handle[A](
      sourceFile: Path,
      outDir: Path,
      methodName: String,
      strategy: Strategy[A]
  )(property: A => Boolean): IO[Unit] = for {
    parsed   <- treeBuilder.build(sourceFile, methodName)
    leaves    = parsed.map(_.leafPositions).getOrElse(Set.empty)
    tree      = parsed.map(_.branchTree)
    feedback <- runScalaCheck(sourceFile, methodName, strategy, leaves, property)
    _        <- writer.write(SessionReport(methodName, sourceFile, tree, strategy.name, feedback), outDir)
  } yield ()

  private def runScalaCheck[A](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy[A],
      leaves: Set[Pos],
      property: A => Boolean
  ): IO[SessionFeedback[A]] = IO.blocking {
    var feedback = SessionFeedback.empty[A]
    val gen      = Gen.delay(strategy.gen(feedback))
    val prop     = Prop.forAll(gen) { input =>
      Try(property(input))
      val fired = sourceCoverage.coverage(sourceFile, methodName)
      feedback = feedback.append(input, fired.intersect(leaves))
      true
    }
    Test.check(params, prop)
    feedback
  }
}
