package usecase

import cats.effect.IO
import cats.effect.unsafe.implicits.global
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
import org.scalacheck.{Arbitrary, Gen, Prop, Test}
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
  * The fuzz loop is `Prop.forAll` + `Test.check` — the same shape you'd write for any normal
  * ScalaCheck test. ScalaCheck owns the loop, size schedule, seed thread, and input generation.
  *
  * Two pragmatic compromises are necessary to make this shape work alongside `IO`-typed driven
  * adapters (since `Prop.forAll`'s body is sync):
  *
  *   1. The body calls `tracker.measure(...).unsafeRunSync()` etc. The bridge is contained to this
  *      one method, runs on the blocking thread pool via `IO.blocking`, and the IO ports themselves
  *      do quick sync file I/O underneath.
  *   2. A method-local `var feedback` for the running accumulator. Single-threaded inside
  *      `Test.check`'s sequential body; can't escape this method.
  *
  * Future-proofing for guided: the `Gen[A]` for [[Strategy.Guided]] is a `Gen.delay` that closes
  * over the running `feedback`, so a real coverage-driven generator plugs in without touching the
  * loop or any port signature.
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
    _        <- sourceCoverage.cleanStaleData
    _        <- tracker.reset
    feedback <- runScalaCheck(sourceFile, methodName, strategy, property)
    tree     <- treeBuilder.build(sourceFile, methodName)
    src      <- sourceCoverage.methodCoverage(sourceFile, methodName)
    _        <- warnOnDrift(methodName, tree, src)
    _        <- writer.write(buildReport(sourceFile, methodName, feedback, tree, src), outDir)
  } yield ()

  private def runScalaCheck[A](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy,
      property: A => Boolean
  )(implicit arb: Arbitrary[A]): IO[SessionFeedback[A]] = IO.blocking {
    val sourceFileName = sourceFile.getFileName.toString
    var feedback = SessionFeedback.empty[A]

    val gen: Gen[A] = strategy match {
      case Strategy.Random => arb.arbitrary
      case Strategy.Guided =>
        // Re-evaluated on every iteration — gives a real guided strategy access to `feedback`.
        Gen.delay {
          println(s"[guided] iter=${feedback.iteration}")
          arb.arbitrary
        }
    }

    val prop = Prop.forAll(gen) { input =>
      Try(property(input))
      val m = tracker.measure(sourceFileName, methodName).unsafeRunSync()
      val src = sourceCoverage.methodCoverage(sourceFile, methodName).unsafeRunSync()
      feedback = step(feedback, input, m, src.branchCounter.covered)
      true
    }
    Test.check(params, prop)
    feedback
  }

  /** Pure transition: previous state + (input, measurement, source-level cumulative covered) → new
    * state.
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
