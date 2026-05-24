package usecase

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import domain.{
  BranchOutcome,
  BranchTree,
  InputRecord,
  MethodSourceCoverage,
  MethodTree,
  Pos,
  SessionFeedback,
  SessionReport,
  Strategy
}
import org.scalacheck.{Arbitrary, Gen, Prop, Test}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}
import usecase.strategy.{FeedbackBiasGuidedGen, MutationGuidedGen, RandomGen}

import java.nio.file.Path
import scala.util.Try

/** Use case for one fuzz session.
  *
  * Pipeline:
  *
  *   1. Run the fuzz loop (`Prop.forAll` + `Test.check`), folding an immutable
  *      [[SessionFeedback]] across iterations.
  *   2. Parse the source for the method's branch tree (for the picture).
  *   3. Read the final scoverage snapshot (for the headline numbers and per-branch lookup).
  *   4. Warn if scoverage's branch count and the tree's arm count disagree (dev signal that the
  *      Scalameta walker is missing a construct).
  *   5. Hand a [[SessionReport]] to the writer.
  *
  * All coverage is source-level — every per-iteration delta comes from diffing scoverage's
  * cumulative state against the running `coveredBranches` set in [[SessionFeedback]].
  *
  * Two pragmatic compromises bridge ScalaCheck's sync `Prop.forAll` body to the `IO`-typed ports:
  *
  *   1. `runScalaCheck` runs inside `IO.blocking` and calls `methodCoverage(...).unsafeRunSync()`
  *      inside the prop body. The bridge is contained to that one method, and the IO underneath
  *      does small sync file reads.
  *   2. A method-local `var feedback` accumulator. Single-threaded inside `Test.check`'s
  *      sequential body; never escapes this method.
  *
  * Future-proofing for guided: the `Gen[A]` for [[Strategy.Guided]] is a `Gen.delay` closure that
  * sees the running `feedback`, so a real coverage-driven generator plugs in without touching the
  * loop or any port signature.
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
  )(property: A => Boolean): IO[Unit] = for {
    _        <- sourceCoverage.cleanStaleData
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
    var feedback = SessionFeedback.empty[A]

    // Each strategy lives in its own module under usecase.strategy; adding a new one is a new
    // case object in Strategy + a new module file + one new arm here.
    val gen: Gen[A] = strategy match {
      case Strategy.Random             => RandomGen.gen[A](feedback)
      case Strategy.MutationGuided     => MutationGuidedGen.gen[A](feedback)
      case Strategy.FeedbackBiasGuided => FeedbackBiasGuidedGen.gen[A](feedback)
    }

    val prop = Prop.forAll(gen) { input =>
      Try(property(input))
      val src = sourceCoverage.methodCoverage(sourceFile, methodName).unsafeRunSync()
      feedback = step(feedback, input, src)
      true
    }
    Test.check(params, prop)
    feedback
  }

  /** Pure transition: previous state + (input, fresh scoverage snapshot) → new state. The diff
    * between scoverage's cumulative `coveredBranchPositions` and the running set gives this
    * input's newly-covered set.
    */
  private def step[A](
      state: SessionFeedback[A],
      input: A,
      src: MethodSourceCoverage
  ): SessionFeedback[A] = {
    val nowCovered = src.coveredBranchPositions
    val newlyCovered = nowCovered -- state.coveredBranches
    val idx = state.iteration
    SessionFeedback(
      history = state.history :+ InputRecord(idx, input, newlyCovered),
      coveredBranches = nowCovered,
      growthCurve = state.growthCurve :+ src.branchCounter.covered
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
    // Each branch's `Pos` appears in at most one record's `newlyCoveredBranches` (the iteration
    // that first covered it), so this flat-map → toMap is unambiguous.
    val firstHits: Map[Pos, Int] = feedback.history.iterator
      .flatMap(rec => rec.newlyCoveredBranches.iterator.map(_ -> rec.index))
      .toMap
    val labels: Map[Pos, String] =
      tree.fold(Map.empty[Pos, String])(t => BranchTree.collectLabels(t.body))
    val branches = src.branchLines.toVector
      .sortBy { case (p, _) => p.offset }
      .map { case (pos, line) =>
        BranchOutcome(pos, line, labels.getOrElse(pos, "?"), firstHits.get(pos))
      }
    SessionReport(
      methodName = methodName,
      sourceFile = sourceFile,
      totalInputs = feedback.iteration,
      methodTree = tree,
      sourceBranchCounter = src.branchCounter,
      branches = branches,
      inputLog = feedback.history,
      growthCurve = feedback.growthCurve,
      saturationInputIndex = saturation,
      coveredPositions = src.coveredPositions
    )
  }
}
