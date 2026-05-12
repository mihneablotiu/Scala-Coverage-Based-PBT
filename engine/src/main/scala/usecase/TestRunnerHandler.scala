package usecase

import cats.effect.IO
import domain.{
  BranchSummary,
  CoverageMeasurement,
  InputRecord,
  MethodTree,
  Pos,
  SessionFeedback,
  SessionReport,
  Strategy
}
import port.driven.{
  BranchCoverageTracker,
  BranchTreeBuilder,
  CoverageReportWriter,
  InputGenerator,
  SourceCoverageReader
}
import port.driving.TestRunner

import java.nio.file.Path

/** The use case behind a `TestRunner` invocation: drives the fuzz loop, accumulates per-line
  * statistics from JaCoCo, and hands a final [[SessionReport]] to the writer.
  *
  * The loop is generator-agnostic — strategy choice resolves to an [[InputGenerator]] outside the
  * handler. Each iteration feeds the generator a [[SessionFeedback]] snapshot, lets it pick an
  * input, runs the property, and re-measures.
  *
  * Per-AST-position coverage (from scoverage via [[SourceCoverageReader]]) is read *only after* the
  * loop has finished — it never influences any main-session statistic, only the writer's per-node
  * colouring.
  */
trait TestRunnerHandler extends TestRunner

object TestRunnerHandler {

  private val NumInputs = 1000

  def apply(
      tracker: BranchCoverageTracker,
      treeBuilder: BranchTreeBuilder,
      sourceCoverage: SourceCoverageReader,
      writer: CoverageReportWriter,
      generators: Strategy => InputGenerator
  ): TestRunnerHandler = new Live(tracker, treeBuilder, sourceCoverage, writer, generators)

  private final class Live(
      tracker: BranchCoverageTracker,
      treeBuilder: BranchTreeBuilder,
      sourceCoverage: SourceCoverageReader,
      writer: CoverageReportWriter,
      generators: Strategy => InputGenerator
  ) extends TestRunnerHandler {

    override def run(
        sourceFile: Path,
        methodName: String,
        property: Int => Boolean,
        strategy: Strategy,
        outDir: Path
    ): IO[Unit] = {
      val generator = generators(strategy)
      val sourceFileName = sourceFile.getFileName.toString

      def step(state: SessionFeedback, input: Int, m: CoverageMeasurement): SessionFeedback = {
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

      def loop(remaining: Int, state: SessionFeedback): IO[SessionFeedback] =
        if (remaining == 0) IO.pure(state)
        else
          for {
            input <- generator.next(state)
            _ <- IO(property(input)).attempt
            measured <- tracker.measure(sourceFileName, methodName)
            done <- loop(remaining - 1, step(state, input, measured))
          } yield done

      def build(
          state: SessionFeedback,
          tree: Option[MethodTree],
          positions: Set[Pos]
      ): SessionReport = {
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
          branchesByLine = branches,
          inputLog = state.history,
          growthCurve = state.growthCurve,
          saturationInputIndex = saturation,
          coveredPositions = positions
        )
      }

      for {
        _ <- sourceCoverage.cleanStaleData
        _ <- tracker.reset
        finalS <- loop(NumInputs, SessionFeedback.empty)
        tree <- treeBuilder.build(sourceFile, methodName)
        positions <- sourceCoverage.coveredPositions(sourceFile, methodName)
        _ <- writer.write(build(finalS, tree, positions), outDir)
      } yield ()
    }
  }
}
