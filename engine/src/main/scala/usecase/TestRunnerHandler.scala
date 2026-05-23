package usecase

import cats.effect.IO
import domain.{
  BranchSummary,
  BranchTree,
  CoverageMeasurement,
  InputRecord,
  MethodSourceCoverage,
  MethodTree,
  SessionFeedback,
  SessionReport,
  Strategy
}
import org.scalacheck.{rng, Arbitrary, Gen, Test}
import port.driven.{
  BranchCoverageTracker,
  BranchTreeBuilder,
  CoverageReportWriter,
  SourceCoverageReader
}

import java.nio.file.Path
import scala.util.Try

/** Use case for one fuzz session.
  *
  * Drives an IO-based loop matching `Test.check`'s semantics exactly — same size schedule, same
  * `seed.next` thread, same `Gen.pureApply` per iteration. State across iterations is the immutable
  * [[SessionFeedback]] folded through a pure [[step]] function.
  */
final class TestRunnerHandler(
    tracker: BranchCoverageTracker,
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
  )(property: A => Boolean): IO[Unit] = for {
    _ <- sourceCoverage.cleanStaleData
    _ <- tracker.reset
    feedback <- runScalaCheck(sourceFile, methodName, strategy, property)
    tree <- treeBuilder.build(sourceFile, methodName)
    src <- sourceCoverage.methodCoverage(sourceFile, methodName)
    _ <- warnOnDrift(methodName, tree, src)
    _ <- writer.write(buildReport(sourceFile, methodName, feedback, tree, src), outDir)
  } yield ()

  /** Manual IO loop replicating `Test.check`'s recipe:
    *   - size = `round(minSize + (maxSize - minSize) * iter / minSuccessfulTests)`
    *   - seed advances via `seed.next` between iterations
    *   - input via `gen.doPureApply(Gen.Parameters.default.withSize(size), seed)`
    */
  private def runScalaCheck[A](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy,
      property: A => Boolean
  )(implicit arb: Arbitrary[A]): IO[SessionFeedback[A]] = {
    val sourceFileName = sourceFile.getFileName.toString
    val total = params.minSuccessfulTests

    def sizeFor(iter: Int): Int = {
      val span = params.maxSize - params.minSize
      Math.round(params.minSize.toDouble + span.toDouble * iter / total).toInt
    }

    def pickInput(state: SessionFeedback[A], seed: rng.Seed): IO[A] = {
      val gp = Gen.Parameters.default.withSize(sizeFor(state.iteration))
      val input = arb.arbitrary.doPureApply(gp, seed).retrieve.get
      IO.whenA(strategy == Strategy.Guided)(IO.println(formatFeedback(state))).as(input)
    }

    def loop(remaining: Int, state: SessionFeedback[A], seed: rng.Seed): IO[SessionFeedback[A]] =
      if (remaining == 0) IO.pure(state)
      else
        for {
          input <- pickInput(state, seed)
          _ <- IO(Try(property(input)))
          m <- tracker.measure(sourceFileName, methodName)
          src <- sourceCoverage.methodCoverage(sourceFile, methodName)
          next = step(state, input, m, src.branchCounter.covered)
          result <- loop(remaining - 1, next, seed.next)
        } yield result

    IO(params.initialSeed.getOrElse(rng.Seed.random())).flatMap { seed =>
      loop(total, SessionFeedback.empty[A], seed)
    }
  }

  /** Pure transition: previous state + (input, measurement, source-level cumulative covered) → new
    * state. The chart and the headline counter speak the same source-level units.
    */
  private def step[A](
      state: SessionFeedback[A],
      input: A,
      m: CoverageMeasurement,
      srcCovered: Int
  ): SessionFeedback[A] = {
    val exercised = m.perInput.iterator.collect { case (l, c) if c.covered > 0 => l }.toSet
    val idx = state.iteration
    SessionFeedback(
      history = state.history :+ InputRecord(idx, input, exercised),
      cumulativeCoverage = m.cumulative,
      hitCountsByLine = exercised.foldLeft(state.hitCountsByLine)((acc, l) =>
        acc.updated(l, acc.getOrElse(l, 0) + 1)
      ),
      firstHitsByLine = exercised.foldLeft(state.firstHitsByLine)((acc, l) =>
        acc.updatedWith(l)(_.orElse(Some(idx)))
      ),
      growthCurve = state.growthCurve :+ srcCovered
    )
  }

  private def formatFeedback[A](state: SessionFeedback[A]): String =
    f"[guided] iter=${state.iteration}%-3d  lines=${state.cumulativeCoverage.size}%-2d  " +
      f"lastInput=${state.history.lastOption.fold("—")(_.input.toString)}"

  private def warnOnDrift(
      methodName: String,
      tree: Option[MethodTree],
      src: MethodSourceCoverage
  ): IO[Unit] = {
    val astArms = tree.fold(0)(t => BranchTree.armCount(t.body))
    val scov = src.branchCounter.total
    IO.unlessA(astArms == 0 || scov == 0 || astArms == scov) {
      IO.println(
        s"[warn] $methodName: source-level branch drift — scoverage reports $scov branch arm(s), " +
          s"BranchTree models $astArms. Picture is missing decision points; add the construct " +
          s"to ScalametaBranchTreeBuilder.visit."
      )
    }
  }

  private def buildReport[A](
      sourceFile: Path,
      methodName: String,
      feedback: SessionFeedback[A],
      tree: Option[MethodTree],
      src: MethodSourceCoverage
  ): SessionReport[A] = {
    val finalCovered = feedback.growthCurve.lastOption.getOrElse(0)
    val saturation =
      Option.when(feedback.growthCurve.nonEmpty)(feedback.growthCurve.indexOf(finalCovered))
    val branches = feedback.cumulativeCoverage.map { case (line, c) =>
      line -> BranchSummary(
        c,
        feedback.hitCountsByLine.getOrElse(line, 0),
        feedback.firstHitsByLine.get(line)
      )
    }
    SessionReport(
      methodName = methodName,
      sourceFile = sourceFile,
      totalInputs = feedback.iteration,
      methodTree = tree,
      sourceBranchCounter = src.branchCounter,
      branchesByLine = branches,
      inputLog = feedback.history,
      growthCurve = feedback.growthCurve,
      saturationInputIndex = saturation,
      coveredPositions = src.coveredPositions
    )
  }
}
