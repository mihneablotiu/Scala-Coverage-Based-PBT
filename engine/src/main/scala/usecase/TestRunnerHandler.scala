package usecase

import domain.{ConstantPool, Mutator, Pooled, Pos, SessionFeedback, SessionReport, Strategy}
import org.scalacheck.{Arbitrary, Gen, Test, rng}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}

import java.nio.file.Path
import scala.util.Try

/** One fuzz session against one method.
  *
  * Three steps: parse the method (tree + leaves + literal pool in one shot), build the strategy from its name + the mined pool, fold an immutable
  * [[SessionFeedback]] over `params.minSuccessfulTests` iterations, hand the resulting [[SessionReport]] to the writer.
  *
  * Pool plumbing lives entirely here so the user-facing `bench` call stays free of mining / `Arbitrary` / `Mutator` ceremony.
  *
  * Why a hand-rolled fold instead of `Test.check`/`Prop.forAll`: `Gen.pureApply(params, seed)` is exactly what ScalaCheck calls per iteration, with
  * the same `minSize → maxSize` linear ramp; the loop only differs in seed advancement (`Seed.next` vs threading through `Gen.R`), both uniform
  * random. Owning the loop lets [[SessionFeedback]] flow purely through a fold.
  */
final class TestRunnerHandler(
    treeBuilder: BranchTreeBuilder,
    sourceCoverage: SourceCoverageReader,
    writer: CoverageReportWriter,
    params: Test.Parameters
) {

  def handle[A: Arbitrary: Mutator: Pooled](
      sourceFile: Path,
      outDir: Path,
      methodName: String,
      strategyName: String
  )(property: A => Boolean): Unit = {
    val parsed   = treeBuilder.build(sourceFile, methodName)
    val tree     = parsed.map(_.branchTree)
    val leaves   = parsed.map(_.leafPositions).getOrElse(Set.empty)
    val pool     = parsed.map(_.constantPool).getOrElse(ConstantPool.empty)
    val strategy = Strategy
      .parse[A](strategyName, pool)
      .getOrElse(throw new IllegalArgumentException(s"unknown strategy: $strategyName"))

    val feedback = runScalaCheck(sourceFile, methodName, strategy, leaves, property)
    val poolUsed = if (strategyName.endsWith("-pool")) pool else ConstantPool.empty
    writer.write(SessionReport(methodName, sourceFile, tree, strategy.name, poolUsed, feedback), outDir)
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
