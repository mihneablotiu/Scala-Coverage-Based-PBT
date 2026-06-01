package usecase

import domain.{BranchTree, ConstantPool, Generatable, Pos, SessionFeedback, SessionReport, Strategy}
import org.scalacheck.{Gen, Prop, Test}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}

import java.nio.file.Path

/** One fuzz session against one method.
  *
  * Parse the method (tree + literal pool), build the strategy from its name + the mined pool, run ScalaCheck over `params.minSuccessfulTests` inputs
  * while snapshotting coverage after each one, hand the resulting [[SessionReport]] to the writer.
  *
  * The loop is ScalaCheck's own (`Test.check` + `Prop.forAllNoShrink`), not a hand-rolled fold: the `random` strategy is then *literally* a plain
  * `Prop.forAll(arbitrary)` draw — the honest baseline the thesis compares against. Coverage observation rides along as a side effect in the property
  * body; the running [[SessionFeedback]] is read back into the generator via `Gen.delay`, so coverage-guided strategies see the corpus grow.
  */
final class TestRunnerHandler(
    treeBuilder: BranchTreeBuilder,
    sourceCoverage: SourceCoverageReader,
    writer: CoverageReportWriter,
    params: Test.Parameters
) {

  def handle[A: Generatable](
      sourceFile: Path,
      outDir: Path,
      methodName: String,
      strategyName: String
  )(property: A => Boolean): Unit = {
    val parsed   = treeBuilder.build(sourceFile, methodName)
    val tree     = parsed.map(_.branchTree)
    val leaves   = tree.fold(Set.empty[Pos])(t => BranchTree.leaves(t).iterator.map(_.pos).toSet)
    val pool     = parsed.map(_.constantPool).getOrElse(ConstantPool.empty)
    val strategy = Strategy
      .parse[A](strategyName, pool)
      .getOrElse(throw new IllegalArgumentException(s"unknown strategy: $strategyName"))

    val feedback = runScalaCheck(sourceFile, methodName, strategy, leaves, property)
    writer.write(SessionReport(methodName, sourceFile.getFileName.toString, tree, strategy.name, strategy.pool, feedback), outDir)
  }

  private def runScalaCheck[A](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy[A],
      leaves: Set[Pos],
      property: A => Boolean
  ): SessionFeedback[A] = {
    var feedback = SessionFeedback.empty[A]

    // `Gen.delay` re-reads `feedback` each draw. `forAllNoShrink` + an always-true body means no shrinking (which would re-draw against a mutating
    // corpus) and no early stop, so the body runs exactly `minSuccessfulTests` times; the SUT call is guarded so a throw still snapshots coverage.
    val gen  = Gen.delay(strategy.gen(feedback))
    val prop = Prop.forAllNoShrink(gen) { (input: A) =>
      try property(input)
      catch { case _: Throwable => () }
      val fired = sourceCoverage.coverage(sourceFile, methodName).intersect(leaves)
      feedback = feedback.append(input, fired)
      true
    }
    Test.check(params.withWorkers(1), prop)
    feedback
  }
}
