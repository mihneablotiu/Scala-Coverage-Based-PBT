package usecase

import domain.{BranchTree, ConstantPool, Generatable, Pos, SessionFeedback, SessionReport, Strategy}
import org.scalacheck.{Gen, Prop, Test}
import port.driven.{BranchTreeBuilder, CoverageReportWriter, SourceCoverageReader}

import java.nio.file.Path

/** One fuzz session against one method: parse it (tree + mined pool), build the named strategy, run ScalaCheck over `params.minSuccessfulTests`
  * inputs while snapshotting coverage after each, and hand the resulting [[SessionReport]] to the writer.
  *
  * The loop is ScalaCheck's own (`Test.check` + `Prop.forAllNoShrink`), not a hand-rolled fold, so the `random` strategy is *literally* a plain
  * `Prop.forAll(arbitrary)` draw — the honest baseline. The running [[SessionFeedback]] is read back into the generator via `Gen.delay`, so guided
  * strategies see the corpus grow.
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
    val parsed = treeBuilder.build(sourceFile, methodName)
    val tree   = parsed.map(_.branchTree)
    val leaves = tree.fold(List.empty[BranchTree.Leaf])(BranchTree.leaves)
    val pool   = parsed.map(_.constantPool).getOrElse(ConstantPool.empty)
    // `coverage-guided[-…]` is autonomous: derive per-leaf path predicates from the source and target
    // whatever is still uncovered using the live coverage in `feedback`. Everything else parses directly.
    val paths                 = tree.map(BranchTree.leafPaths).getOrElse(Map.empty)
    val nParams               = parsed.map(_.paramCount).getOrElse(1)
    val strategy: Strategy[A] =
      Strategy
        .buildCoverage[A](strategyName, paths, nParams, pool)
        .orElse(Strategy.parse[A](strategyName, pool))
        .getOrElse(throw new IllegalArgumentException(s"unknown strategy: $strategyName"))

    val feedback = runScalaCheck(sourceFile, strategy, leaves, property)
    writer.write(SessionReport(methodName, sourceFile.getFileName.toString, tree, strategy.name, strategy.pool, feedback), outDir)
  }

  private def runScalaCheck[A](
      sourceFile: Path,
      strategy: Strategy[A],
      leaves: List[BranchTree.Leaf],
      property: A => Boolean
  ): SessionFeedback[A] = {
    var feedback = SessionFeedback.empty[A]

    // `Gen.delay` re-reads `feedback` each draw. `forAllNoShrink` + an always-true body means no
    // shrinking and no early stop, so the body runs exactly `minSuccessfulTests` times; the SUT call
    // is guarded so a throw still lets us snapshot coverage.
    val gen  = Gen.delay(strategy.gen(feedback))
    val prop = Prop.forAllNoShrink(gen) { (input: A) =>
      try property(input)
      catch { case _: Throwable => () }
      val fired = sourceCoverage.coverage(sourceFile)
      feedback = feedback.append(input, covered(leaves, fired))
      true
    }
    Test.check(params.withWorkers(1), prop)
    feedback
  }

  /** Positions of the leaves some fired statement falls inside (see [[BranchTree.Leaf.spanContains]]). */
  private def covered(leaves: List[BranchTree.Leaf], fired: Set[Pos]): Set[Pos] =
    leaves.iterator.filter(l => fired.exists(l.spanContains)).map(_.pos).toSet
}
