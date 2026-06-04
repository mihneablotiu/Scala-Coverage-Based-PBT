package pbt

import org.scalacheck.{Gen, Prop, Test, rng}
import pbt.analysis.Parser
import pbt.gen.{ConstantPool, Generatable}
import pbt.strategy.{Feedback, Strategy}

import java.nio.file.Path

/** The framework's entry point. Construct one per SUT root (it reads the instrumented coverage data once), then [[check]] any number of methods: give
  * a method (its source file + name), a property over the method's input type, and a strategy.
  *
  * `random` is plain ScalaCheck. Every other strategy runs the identical loop and just biases some draws off the live [[Feedback]]: `pool` injects
  * mined literals while a leaf is still uncovered, `mutation` perturbs a seed that previously grew coverage. So the baseline stays honest.
  */
final class Pbt(sutRoot: Path) {

  private val coverage = new Coverage(sutRoot)

  def check[A: Generatable](
      sourceFile: Path,
      method: String,
      strategy: Strategy,
      seed: Long = 0L,
      inputs: Int = 10000
  )(property: A => Boolean): Report[A] = {
    val g      = Generatable[A]
    val parsed = Parser.parse(sourceFile, method)
    val tree   = parsed.map(_.tree)
    val leaves = tree.toList.flatMap(_.leaves)
    val pool   = parsed.map(_.pool)

    var feedback = Feedback.empty[A]

    // The next input: a plain random draw, plus pooled/mutated draws when the strategy enables them and the feedback says they can still help. With
    // none enabled this is exactly `arbitrary` — i.e. stock ScalaCheck. `Gen.delay` re-reads `feedback` every draw, so the bias tracks live coverage.
    def nextInput: Gen[A] = {
      val poolDraw     = if (strategy.pool) pool.filter(p => leaves.exists(l => !feedback.covered(l.start))).map(g.pooled) else None
      val mutationDraw = if (strategy.mutation && feedback.corpus.nonEmpty) Some(g.mutate(feedback.corpus.last)) else None
      val biased       = poolDraw.toList ++ mutationDraw.toList
      if (biased.isEmpty) g.arbitrary else Gen.frequency((1 -> g.arbitrary) :: biased.map(4 -> _): _*)
    }

    val prop = Prop.forAllNoShrink(Gen.delay(nextInput)) { input =>
      try property(input)
      catch { case _: Throwable => () }
      val fired = coverage.firedOffsets(sourceFile)
      feedback = feedback.record(input, leaves.filter(l => fired.exists(l.contains)).map(_.start).toSet)
      true
    }

    val t0 = System.nanoTime()
    Test.check(Test.Parameters.default.withInitialSeed(rng.Seed(seed)).withMinSuccessfulTests(inputs).withWorkers(1), prop)
    val elapsedMillis = (System.nanoTime() - t0) / 1000000L

    Report(method, sourceFile.getFileName.toString, strategy.name, tree, pool.getOrElse(ConstantPool.empty), feedback, elapsedMillis)
  }
}
