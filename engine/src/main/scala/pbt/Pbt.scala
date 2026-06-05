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

    def nextInput: Gen[Tactic.Candidate[A]] = {
      val context   = Tactic.Context(g, feedback, targetIds, pool)
      val proposals = strategy.tactics.flatMap(tactic => tactic.propose(context).map(tactic.name -> _))
      val random    = g.arbitrary.map(input => Tactic.Candidate("random", input))
      val sources   = "random" :: proposals.map(_._1)
      val weighted  = (1 -> random) :: proposals.map { case (_, gen) => 4 -> gen }
      val mixed     = Gen.frequency(weighted: _*)
      mixed.map(candidate => candidate.copy(availableSources = sources))
    }

    val prop = Prop.forAllNoShrink(Gen.delay(nextInput)) { input =>
      try property(input.input)
      catch { case _: Throwable => () }
      feedback = feedback.record(input, coverage.firedTargetIds(sourceFile, targets).intersect(targetIds))
      true
    }

    val t0 = System.nanoTime()
    Test.check(Test.Parameters.default.withInitialSeed(rng.Seed(seed)).withMinSuccessfulTests(inputs).withWorkers(1), prop)
    val elapsedMillis = (System.nanoTime() - t0) / 1000000L

    Report(method, sourceFile.getFileName.toString, strategy.name, targets, pool.getOrElse(ConstantPool.empty), feedback, elapsedMillis)
  }
}
