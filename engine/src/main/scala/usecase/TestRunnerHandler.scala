package usecase

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import domain.{BranchTree, MethodTree, Pos, SessionFeedback, SessionReport, Strategy}
import org.scalacheck.{Gen, Prop, Test}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}

import java.nio.file.Path
import scala.util.Try

/** Use case for one fuzz session.
  *
  * Pipeline:
  *
  *   1. Parse the source for the method's branch tree once — the leaves of that tree are the
  *      canonical "branches" for the rest of the session, so we need them *before* the loop runs.
  *   2. Run the fuzz loop (`Prop.forAll` + `Test.check`) over a `Gen.delay`-wrapped
  *      `strategy.gen(feedback)` so the strategy is re-consulted on every iteration with the latest
  *      [[SessionFeedback]]. Each iteration intersects scoverage's fired positions with the leaf
  *      set to get the input's "new branches".
  *   3. Read scoverage's final fired-positions snapshot once at the end (for the DOT graph
  *      colouring, which paints every node — leaves *and* decision points).
  *   4. Hand a [[SessionReport]] to the writer.
  *
  * All coverage is source-level. Two layers keep that honest:
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
  *      body; never escapes this method. `Gen.delay` is what bridges the mutable feedback into the
  *      (otherwise pure) generator — each iteration, ScalaCheck calls the thunk fresh, which reads
  *      the current `feedback` and asks the strategy for the next `Gen[A]`.
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
  )(exercise: A => Any): IO[Unit] = for {
    tree <- treeBuilder.build(sourceFile, methodName)
    leaves = leafPositions(tree)
    feedback <- runScalaCheck(sourceFile, methodName, strategy, leaves, exercise)
    fired    <- sourceCoverage.coverage(sourceFile, methodName)
    _        <- writer.write(SessionReport(methodName, sourceFile, tree, fired, feedback), outDir)
  } yield ()

  private def leafPositions(tree: Option[MethodTree]): Set[Pos] =
    tree.fold(Set.empty[Pos])(t => BranchTree.leaves(t.body).iterator.map(_.pos).toSet)

  private def runScalaCheck[A](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy[A],
      leaves: Set[Pos],
      exercise: A => Any
  ): IO[SessionFeedback[A]] = IO.blocking {
    var feedback = SessionFeedback.empty[A]
    val gen = Gen.delay(strategy.gen(feedback))
    val prop = Prop.forAll(gen) { input =>
      Try(exercise(input))
      val fired = sourceCoverage.coverage(sourceFile, methodName).unsafeRunSync()
      feedback = feedback.append(input, fired.intersect(leaves))
      true
    }
    Test.check(params, prop)
    feedback
  }
}
