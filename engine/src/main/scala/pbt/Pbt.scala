package pbt

import org.scalacheck.{Gen, Prop, Test, rng}
import pbt.analysis.Parser
import pbt.gen.{ConstantPool, Generatable}
import pbt.strategy.{Feedback, Strategy, TacticContext}
import pbt.targeting.{OptionalNumericFields, TargetMapper}

import java.nio.file.Path

final class Pbt(sutRoot: Path) {

  private val coverage = new Coverage(sutRoot)

  def check[A: Generatable: OptionalNumericFields](
      sourceFile: Path,
      method: String,
      strategy: Strategy,
      seed: Long = 0L,
      inputs: Int = 10000
  )(property: A => Boolean): Report[A] = {
    val generatable = Generatable[A]
    val targets     = coverage.methodStatements(sourceFile, method)
    val pool        = Parser.literalPool(sourceFile, method).getOrElse(ConstantPool.empty)
    val targetGoals =
      if (strategy.usesTargeting && OptionalNumericFields[A].instance.isDefined) {
        val predicates = Parser.numericPredicates(sourceFile, method)
        val goals      = TargetMapper.goals(targets, predicates)
        if (predicates.nonEmpty && goals.isEmpty)
          Console.err.println(
            s"[targeting] $method: parsed ${predicates.size} numeric predicate(s) but none mapped to a coverage branch; falling back to random"
          )
        goals
      } else Nil

    var feedback = Feedback.empty[A]

    def nextInput: Gen[A] = {
      val context = TacticContext(generatable, feedback, targets, pool, targetGoals)
      strategy.next(context)
    }

    val prop = Prop.forAllNoShrink(Gen.delay(nextInput)) { input =>
      val ok =
        try property(input)
        catch { case _: Throwable => false }
      feedback = feedback.record(input, coverage.firedTargetIds(sourceFile, targets), targetGoals)
      ok
    }

    val t0 = System.nanoTime()
    Test.check(Test.Parameters.default.withInitialSeed(rng.Seed(seed)).withMinSuccessfulTests(inputs).withWorkers(1), prop)
    val elapsedMillis = (System.nanoTime() - t0) / 1000000L

    Report(method, sourceFile.getFileName.toString, strategy.name, targets, pool, feedback, elapsedMillis)
  }
}
