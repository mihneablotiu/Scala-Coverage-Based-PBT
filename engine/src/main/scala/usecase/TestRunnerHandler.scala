package usecase

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import domain.{SessionFeedback, SessionReport, Strategy}
import org.scalacheck.{Arbitrary, Prop, Test}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}

import java.nio.file.Path
import scala.util.Try

/** Use case for one fuzz session.
  *
  * Pipeline:
  *
  *   1. Run the fuzz loop (`Prop.forAll` + `Test.check`) using `strategy.gen[A]`, folding an
  *      immutable [[SessionFeedback]] across iterations.
  *   2. Parse the source for the method's branch tree (for the picture).
  *   3. Read the final scoverage snapshot (for the headline numbers and per-branch lookup).
  *   4. Hand a [[SessionReport]] to the writer.
  *
  * All coverage is source-level — every per-iteration delta comes from diffing the in-session
  * `coveredBranches` set against scoverage's cumulative state. Two layers keep that diff honest:
  *
  *   - **JVM isolation per strategy** (see `app.Main`): each `runMain` forks a fresh JVM, so
  *     scoverage's process-global `Invoker` only ever holds one strategy's hits.
  *   - **Per-method filtering** (see `ScoverageSourceCoverageReader.coverage`): within a JVM,
  *     scoverage accumulates across every benchmark, but each query is scoped to the asked-for
  *     `(sourceFile, methodName)`, so a report only ever sees statements that belong to that
  *     specific method.
  *
  * Two pragmatic compromises bridge ScalaCheck's sync `Prop.forAll` body to the `IO`-typed ports:
  *
  *   1. `runScalaCheck` runs inside `IO.blocking` and calls `coverage(...).unsafeRunSync()` inside
  *      the prop body. The bridge is contained to that one method, and the IO underneath does small
  *      sync file reads.
  *   2. A method-local `var feedback` accumulator. Single-threaded inside `Test.check`'s sequential
  *      body; never escapes this method.
  */
final class TestRunnerHandler(
    treeBuilder: BranchTreeBuilder,
    sourceCoverage: SourceCoverageReader,
    writer: CoverageReportWriter,
    params: Test.Parameters
) {

  def handle[A: Arbitrary](
      sourceFile: Path,
      outDir: Path,
      methodName: String,
      strategy: Strategy
  )(exercise: A => Any): IO[Unit] = for {
    feedback <- runScalaCheck(sourceFile, methodName, strategy, exercise)
    tree     <- treeBuilder.build(sourceFile, methodName)
    coverage <- sourceCoverage.coverage(sourceFile, methodName)
    _ <- writer.write(SessionReport(methodName, sourceFile, tree, coverage, feedback), outDir)
  } yield ()

  private def runScalaCheck[A: Arbitrary](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy,
      exercise: A => Any
  ): IO[SessionFeedback[A]] = IO.blocking {
    var feedback = SessionFeedback.empty[A]
    val prop = Prop.forAll(strategy.gen[A]) { input =>
      Try(exercise(input))
      val src = sourceCoverage.coverage(sourceFile, methodName).unsafeRunSync()
      feedback = feedback.append(input, src.coveredBranchPositions)
      true
    }
    Test.check(params, prop)
    feedback
  }
}
