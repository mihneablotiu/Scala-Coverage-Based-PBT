package pbt

import org.scalacheck.{Gen, Prop, Test, rng}
import pbt.analysis.{BranchTree, Parser}
import pbt.gen.{ConstantPool, Generatable}
import pbt.strategy.{Strategy, Tactic}

import java.nio.file.Path

/** The framework's entry point. Give it a method (its source file + name), a property over that method's input type, and a strategy; it generates
  * `inputs` values, runs the property on each while measuring coverage, and returns a [[Report]].
  *
  * `random` is plain ScalaCheck (`Prop.forAll(arbitrary)`). Every other strategy is the same loop plus a set of coverage-guided tactics that read the
  * live [[Feedback]] to bias the next draw — and the loop is identical regardless, so the baseline is honest.
  */
object Pbt {

  def check[A: Generatable](
      sourceFile: Path,
      method: String,
      strategy: Strategy,
      coverage: Coverage,
      seed: Long = 0L,
      inputs: Int = 10000
  )(property: A => Boolean): Report[A] = {
    val g       = Generatable[A]
    val parsed  = Parser.parse(sourceFile, method)
    val tree    = parsed.map(_.tree)
    val leaves  = tree.fold(List.empty[BranchTree.Leaf])(BranchTree.leaves)
    val tactics = parsed.fold(List.empty[Tactic[A]])(pm => strategy.tactics.toList.map(Tactic.of(_, g, pm)))
    val pool    = tree.fold(ConstantPool.empty)(BranchTree.leafLiterals(_).values.foldLeft(ConstantPool.empty)(_ ++ _))

    var feedback = Feedback.empty[A]

    // `Gen.delay` re-reads `feedback` each draw; `forAllNoShrink` + an always-true body runs the loop exactly `inputs` times with no shrinking. The
    // SUT call is guarded so a thrown exception still lets us snapshot coverage.
    val gen  = Gen.delay(mix(g.arbitrary, tactics.flatMap(_.propose(feedback))))
    val prop = Prop.forAllNoShrink(gen) { (input: A) =>
      try property(input)
      catch { case _: Throwable => () }
      val nowCovered = covered(leaves, coverage.firedOffsets(sourceFile))
      feedback = feedback.record(input, nowCovered)
      tactics.foreach(_.observe(input, feedback))
      true
    }
    Test.check(Test.Parameters.default.withInitialSeed(rng.Seed(seed)).withMinSuccessfulTests(inputs).withWorkers(1), prop)

    Report(method, sourceFile.getFileName.toString, strategy.name, tree, feedback, pool)
  }

  /** The leaves some fired offset lands inside — leaf-only branch coverage. */
  private def covered(leaves: List[BranchTree.Leaf], fired: Set[Pos]): Set[Pos] =
    leaves.iterator.filter(l => fired.exists(l.contains)).map(_.pos).toSet

  /** Mix the active tactics' proposals with a plain random draw, all equally weighted — so random stays a healthy escape hatch (≈ 1/(tactics+1) of
    * draws) that keeps exploring branches no tactic can target. No proposals ⇒ pure random ⇒ stock ScalaCheck.
    */
  private def mix[A](random: Gen[A], proposals: List[Gen[A]]): Gen[A] =
    if (proposals.isEmpty) random
    else Gen.frequency(((1, random) :: proposals.map((1, _))): _*)
}
