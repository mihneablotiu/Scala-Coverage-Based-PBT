package pbt

import org.scalacheck.{Gen, Prop, Test, rng}
import pbt.analysis.Parser
import pbt.gen.{ConstantPool, Generatable}
import pbt.strategy.{Feedback, Strategy, Tactic}

import java.nio.file.Path

final class Pbt(sutRoot: Path) {

  private val coverage = new Coverage(sutRoot)

  def check[A: Generatable](
      sourceFile: Path,
      method: String,
      strategy: Strategy,
      seed: Long = 0L,
      inputs: Int = 10000
  )(property: A => Boolean): Report[A] = {
    val g         = Generatable[A]
    val parsed    = Parser.parse(sourceFile, method)
    val targets   = parsed.toList.flatMap(p => coverage.methodStatements(sourceFile, p.span))
    val targetIds = targets.map(_.id).toSet
    val pool      = parsed.map(_.pool)

    var feedback = Feedback.empty[A]

    def nextInput: Gen[A] = {
      val biased = strategy.tactics.flatMap(propose(g, _, feedback, targetIds, pool))
      if (biased.isEmpty) g.arbitrary else Gen.frequency((1 -> g.arbitrary) :: biased.map(4 -> _): _*)
    }

    val prop = Prop.forAllNoShrink(Gen.delay(nextInput)) { input =>
      try property(input)
      catch { case _: Throwable => () }
      feedback = feedback.record(input, coverage.firedTargetIds(sourceFile, targets).intersect(targetIds))
      true
    }

    val t0 = System.nanoTime()
    Test.check(Test.Parameters.default.withInitialSeed(rng.Seed(seed)).withMinSuccessfulTests(inputs).withWorkers(1), prop)
    val elapsedMillis = (System.nanoTime() - t0) / 1000000L

    Report(method, sourceFile.getFileName.toString, strategy.name, targets, pool.getOrElse(ConstantPool.empty), feedback, elapsedMillis)
  }

  private def propose[A](
      g: Generatable[A],
      tactic: Tactic,
      feedback: Feedback[A],
      targetIds: Set[Int],
      pool: Option[ConstantPool]
  ): Option[Gen[A]] =
    tactic match {
      case Tactic.Pool =>
        pool.filter(p => !p.isEmpty && targetIds.exists(id => !feedback.covered(id))).flatMap(g.pooled)
      case Tactic.Mutation =>
        feedback.corpus.lastOption.map(g.mutate)
    }
}
