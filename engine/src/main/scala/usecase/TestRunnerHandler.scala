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
import org.scalacheck.Gen
import port.driven.{
  BranchCoverageTracker,
  BranchTreeBuilder,
  CoverageReportWriter,
  GeneratorFactory,
  SourceCoverageReader
}
import port.driving.TestRunner

import java.nio.file.Path

/** The use case behind a `TestRunner` invocation: drives the fuzz loop, accumulates per-input
  * statistics from JaCoCo, and hands a final [[SessionReport]] to the writer.
  *
  * The loop is generator-agnostic — strategy choice resolves to an [[port.driven.InputGenerator]]
  * outside the handler. Each iteration feeds the generator a [[SessionFeedback]] snapshot, lets it
  * pick an input, runs the property, and re-measures.
  *
  * Source-level coverage (from scoverage via [[SourceCoverageReader]]) is read *only after* the
  * loop has finished. It supplies the authoritative headline `sourceBranchCounter` and the AST
  * colouring set — never any per-input statistic.
  *
  * A drift check fires when scoverage knows about branch positions the Scalameta AST didn't
  * enumerate — that means the DOT picture is missing decision points and `BranchTree` needs a new
  * variant (e.g. `try`, `for { _ <- _ if _ }`, case guards). Loud over silent.
  *
  * Parameterised over the input type `A`.
  */
trait TestRunnerHandler extends TestRunner

object TestRunnerHandler {

  private val NumInputs = 100

  def apply(
      tracker: BranchCoverageTracker,
      treeBuilder: BranchTreeBuilder,
      sourceCoverage: SourceCoverageReader,
      writer: CoverageReportWriter,
      generators: GeneratorFactory
  ): TestRunnerHandler = new Live(tracker, treeBuilder, sourceCoverage, writer, generators)

  private final class Live(
      tracker: BranchCoverageTracker,
      treeBuilder: BranchTreeBuilder,
      sourceCoverage: SourceCoverageReader,
      writer: CoverageReportWriter,
      generators: GeneratorFactory
  ) extends TestRunnerHandler {

    override def run[A](
        sourceFile: Path,
        methodName: String,
        property: A => Boolean,
        strategy: Strategy,
        gen: Gen[A],
        outDir: Path
    ): IO[Unit] = {
      val generator = generators.make(strategy, gen)
      val sourceFileName = sourceFile.getFileName.toString

      def step(
          state: SessionFeedback[A],
          input: A,
          m: CoverageMeasurement
      ): SessionFeedback[A] = {
        val exercised = m.perInput.iterator.collect {
          case (line, c) if c.covered > 0 => line
        }.toSet
        val cumulCov = m.cumulative.values.iterator.map(_.covered).sum
        val idx = state.iteration
        SessionFeedback(
          history = state.history :+ InputRecord(idx, input, exercised),
          cumulativeCoverage = m.cumulative,
          hitCountsByLine = exercised.foldLeft(state.hitCountsByLine)((acc, l) =>
            acc.updated(l, acc.getOrElse(l, 0) + 1)
          ),
          firstHitsByLine = exercised.foldLeft(state.firstHitsByLine)((acc, l) =>
            if (acc.contains(l)) acc else acc.updated(l, idx)
          ),
          growthCurve = state.growthCurve :+ cumulCov
        )
      }

      def loop(remaining: Int, state: SessionFeedback[A]): IO[SessionFeedback[A]] =
        if (remaining == 0) IO.pure(state)
        else
          for {
            input <- generator.next(state)
            _ <- IO(property(input)).attempt
            measured <- tracker.measure(sourceFileName, methodName)
            done <- loop(remaining - 1, step(state, input, measured))
          } yield done

      def build(
          state: SessionFeedback[A],
          tree: Option[MethodTree],
          src: MethodSourceCoverage
      ): SessionReport[A] = {
        val finalCovered = state.growthCurve.lastOption.getOrElse(0)
        val saturation =
          if (state.growthCurve.isEmpty) None else Some(state.growthCurve.indexOf(finalCovered))
        val branches = state.cumulativeCoverage.iterator.map { case (line, c) =>
          line -> BranchSummary(
            c,
            state.hitCountsByLine.getOrElse(line, 0),
            state.firstHitsByLine.get(line)
          )
        }.toMap
        SessionReport(
          methodName = methodName,
          sourceFile = sourceFile,
          totalInputs = state.iteration,
          methodTree = tree,
          sourceBranchCounter = src.branchCounter,
          branchesByLine = branches,
          inputLog = state.history,
          growthCurve = state.growthCurve,
          saturationInputIndex = saturation,
          coveredPositions = src.coveredPositions
        )
      }

      for {
        _ <- sourceCoverage.cleanStaleData
        _ <- tracker.reset
        finalS <- loop(NumInputs, SessionFeedback.empty[A])
        tree <- treeBuilder.build(sourceFile, methodName)
        src <- sourceCoverage.methodCoverage(sourceFile, methodName)
        _ <- warnOnDrift(methodName, tree, src)
        _ <- writer.write(build(finalS, tree, src), outDir)
      } yield ()
    }

    /** Compare scoverage's branch count against the Scalameta-derived tree. A mismatch means
      * the picture is missing decision points that scoverage instrumented; the only fix is a new
      * case in [[adapter.driven.scalameta.ScalametaBranchTreeBuilder]] for the construct that
      * triggered it (e.g. `try`/`for`/case guards).
      */
    private def warnOnDrift(
        methodName: String,
        tree: Option[MethodTree],
        src: MethodSourceCoverage
    ): IO[Unit] = {
      val astArms = tree.fold(0)(t => BranchTree.armCount(t.body))
      val scov = src.branchCounter.total
      if (astArms == 0 || scov == 0 || astArms == scov) IO.unit
      else
        IO.println(
          s"[warn] $methodName: source-level branch drift — scoverage reports $scov branch arm(s), " +
            s"BranchTree models $astArms. Picture is missing decision points; add the construct " +
            s"to ScalametaBranchTreeBuilder.visit."
        )
    }
  }
}
