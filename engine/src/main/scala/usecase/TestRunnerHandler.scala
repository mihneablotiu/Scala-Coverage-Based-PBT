package usecase

import domain.{Pos, SessionFeedback, SessionReport, Strategy}
import org.scalacheck.{Gen, Test, rng}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}

import java.nio.file.Path
import scala.util.Try

/** One fuzz session against one method.
  *
  * Three steps: parse the method (tree + leaf positions in one shot), fold an immutable [[SessionFeedback]] over `params.minSuccessfulTests`
  * iterations driven by ScalaCheck's `Gen` API (each iteration re-consults the strategy with the latest feedback), hand the resulting
  * [[SessionReport]] to the writer.
  *
  * The property is always evaluated (`Try(property(input))` so a thrown predicate doesn't abort the loop) but its boolean result is currently
  * discarded — coverage measurement keeps going regardless of the predicate's outcome.
  *
  * Why a hand-rolled fold instead of `Test.check`/`Prop.forAll`: ScalaCheck's driver hands the per-iteration callback an `A => Boolean`, so the
  * accumulator can only be threaded by mutating a closure variable. Replacing the driver with a `foldLeft` over `Iterator.iterate(seed)(_.next)`
  * keeps the same input/seed/size semantics while letting [[SessionFeedback]] flow through purely. ScalaCheck still owns generator + RNG; only the
  * loop body is ours.
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
  )(property: A => Boolean): Unit = {
    val parsed   = treeBuilder.build(sourceFile, methodName)
    val leaves   = parsed.map(_.leafPositions).getOrElse(Set.empty)
    val tree     = parsed.map(_.branchTree)
    val feedback = runScalaCheck(sourceFile, methodName, strategy, leaves, property)
    writer.write(SessionReport(methodName, sourceFile, tree, strategy.name, feedback), outDir)
  }

  private def runScalaCheck[A](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy[A],
      leaves: Set[Pos],
      property: A => Boolean
  ): SessionFeedback[A] = {
    val numTests = params.minSuccessfulTests
    val seed0    = params.initialSeed.getOrElse(rng.Seed.random())
    val baseGen  = Gen.Parameters.default
    // Match ScalaCheck's `Test.check` size schedule: linearly grow `Gen.size` from `params.minSize`
    // to `params.maxSize` over `numTests` iterations so list/string lengths follow the usual ramp.
    val sizeStep = (params.maxSize - params.minSize).toDouble / math.max(numTests, 1)

    Iterator
      .iterate(seed0)(_.next)
      .take(numTests)
      .zipWithIndex
      .foldLeft(SessionFeedback.empty[A]) { case (fb, (seed, i)) =>
        val size  = (params.minSize + sizeStep * i).toInt
        val input = strategy.gen(fb).pureApply(baseGen.withSize(size), seed)
        Try(property(input))
        val fired = sourceCoverage.coverage(sourceFile, methodName)
        fb.append(input, fired.intersect(leaves))
      }
  }
}
