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
  * The session is just a `Prop.forAll` driven by `Test.check` — the same as any normal ScalaCheck
  * test, with one extra side-effect inside the property body that records coverage. ScalaCheck
  * owns the loop, the size schedule, the seed thread, and the input generation.
  *
  * Guided strategy hook: the `Gen[A]` handed to `forAll` for [[Strategy.Guided]] is a `Gen.delay`
  * that closes over the running feedback, so a real coverage-driven generator can be plugged in
  * without touching the loop.
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
  )(property: A => Boolean): IO[Unit] = IO.blocking {
    sourceCoverage.cleanStaleData()
    tracker.reset()
    val sourceFileName = sourceFile.getFileName.toString

    // Local accumulator. Mutated only inside Test.check's single-threaded body below; never
    // escapes this method. The mutation is the price of using `Prop.forAll`'s sync signature
    // (which returns Boolean, not a state-threading effect).
    var feedback = SessionFeedback.empty[A]

    val gen: Gen[A] = strategy match {
      case Strategy.Random => implicitly[Arbitrary[A]].arbitrary
      case Strategy.Guided =>
        // Re-evaluated on every iteration — gives a real guided strategy access to the running
        // `feedback` to decide what to emit next. Placeholder for now: log and delegate.
        Gen.delay {
          println(formatFeedback(feedback))
          implicitly[Arbitrary[A]].arbitrary
        }
    }

    val prop = Prop.forAll(gen) { input =>
      Try(property(input))
      val m = tracker.measure(sourceFileName, methodName)
      val srcCov = sourceCoverage.methodCoverage(sourceFile, methodName)
      feedback = step(feedback, input, m, srcCov.branchCounter.covered)
      true
    }
    Test.check(params, prop)

    val tree = treeBuilder.build(sourceFile, methodName)
    val src = sourceCoverage.methodCoverage(sourceFile, methodName)
    warnOnDrift(methodName, tree, src)
    writer.write(buildReport(sourceFile, methodName, feedback, tree, src), outDir)
  }

  /** Pure transition: previous state + (input, measurement, source-level cumulative covered) →
    * new state.
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
  ): Unit = {
    val astArms = tree.fold(0)(t => BranchTree.armCount(t.body))
    val scov = src.branchCounter.total
    if (astArms != 0 && scov != 0 && astArms != scov)
      println(
        s"[warn] $methodName: source-level branch drift — scoverage reports $scov branch arm(s), " +
          s"BranchTree models $astArms. Picture is missing decision points; add the construct " +
          s"to ScalametaBranchTreeBuilder.visit."
      )
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
