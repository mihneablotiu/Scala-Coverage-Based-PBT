package app

import app.Generators.tree // Generatable[Tree] — a user-type instance, the extension point
import benchmark._
import pbt.Pbt
import pbt.gen.Generatable
import pbt.strategy.Strategy

import java.nio.file.{Path, Paths}

/** Experiment harness: runs every benchmark method against one (strategy, seed) pair from the CLI.
  *
  * One forked JVM per pair (scoverage's `Invoker` is process-global with no notion of a session, so a shared JVM would leak coverage between runs).
  * The Makefile sweeps strategies × seeds; each report lands at `engine/reports/statistics/<category>/<method>/<strategy>/seed=NN/coverage.json`.
  *
  * This is *experiment* wiring. A user runs a single property with one call — see [[Pbt.check]].
  */
object Main {

  private val SutRoot     = Paths.get("sut")
  private val ReportsBase = Paths.get("engine/reports/statistics")
  private val Inputs      = 10000

  private def source(category: String): Path = Paths.get(s"sut/src/main/scala/benchmark/$category.scala")

  def main(args: Array[String]): Unit =
    (args.headOption.flatMap(Strategy.byName), args.lift(1).flatMap(_.toLongOption)) match {
      case (Some(strategy), Some(seed)) => runAll(strategy, seed)
      case _                            =>
        println("usage: engine/runMain app.Main <strategy> <seed>")
        println(s"  strategies: ${Strategy.names.mkString(", ")}")
        sys.exit(1)
    }

  private def runAll(strategy: Strategy, seed: Long): Unit = {
    val pbt = new Pbt(SutRoot)

    def out(category: String, method: String): Path =
      ReportsBase.resolve(category).resolve(method).resolve(strategy.name).resolve(f"seed=$seed%02d")

    // The SUT methods return String/Boolean; coverage is measured regardless of the verdict, so the property just exercises the method.
    def bench[A: Generatable](category: String, method: String)(body: A => Any): Unit =
      pbt.check[A](source(category), method, strategy, seed, Inputs)(in => { body(in); true }).write(out(category, method))
    def bench2[A: Generatable, B: Generatable](category: String, method: String)(body: (A, B) => Any): Unit =
      pbt.check2[A, B](source(category), method, strategy, seed, Inputs)((a, b) => { body(a, b); true }).write(out(category, method))
    def bench3[A: Generatable, B: Generatable, C: Generatable](category: String, method: String)(body: (A, B, C) => Any): Unit =
      pbt.check3[A, B, C](source(category), method, strategy, seed, Inputs)((a, b, c) => { body(a, b, c); true }).write(out(category, method))

    bench[Int]("Saturated", "sign")(Saturated.sign)
    bench[List[Int]]("Saturated", "headSign")(Saturated.headSign)
    bench2[Boolean, Boolean]("Saturated", "boolGate")(Saturated.boolGate)
    bench[Int]("Saturated", "smallRange")(Saturated.smallRange)

    bench[String]("Literals", "accessLevel")(Literals.accessLevel)
    bench[String]("Literals", "httpMethod")(Literals.httpMethod)
    bench[String]("Literals", "featureToggle")(Literals.featureToggle)
    bench[Option[Int]]("Literals", "routeOption")(Literals.routeOption)
    bench[Map[String, Int]]("Literals", "lookupRole")(Literals.lookupRole)

    bench[Int]("NumericSearch", "squareTarget")(NumericSearch.squareTarget)
    bench[Int]("NumericSearch", "cubeTarget")(NumericSearch.cubeTarget)
    bench[Int]("NumericSearch", "narrowWindow")(NumericSearch.narrowWindow)
    bench[Int]("NumericSearch", "tightBand")(NumericSearch.tightBand)
    bench2[Int, Int]("NumericSearch", "product")(NumericSearch.product)
    bench2[Int, Int]("NumericSearch", "difference")(NumericSearch.difference)
    bench2[Int, Int]("NumericSearch", "compareInts")(NumericSearch.compareInts)
    bench3[Int, Int, Int]("NumericSearch", "pythagorean")(NumericSearch.pythagorean)

    bench[Double]("Edges", "magnitude")(Edges.magnitude)
    bench[Double]("Edges", "nearPi")(Edges.nearPi)
    bench[Double]("Edges", "floatClass")(Edges.floatClass)

    bench[String]("Frontier", "parseVersion")(Frontier.parseVersion)
    bench[String]("Frontier", "isValidIp")(Frontier.isValidIp)
    bench[String]("Frontier", "balancedBrackets")(Frontier.balancedBrackets)
    bench[List[Int]]("Frontier", "isStrictlySorted")(Frontier.isStrictlySorted)
    bench[benchmark.data.Tree]("Frontier", "bstShape")(Frontier.bstShape)
    bench2[List[Int], List[Int]]("Frontier", "isReverseOf")(Frontier.isReverseOf)
    bench2[Map[String, Int], Map[String, Int]]("Frontier", "sameKeys")(Frontier.sameKeys)
    bench[List[Int]]("Frontier", "luhnCheck")(Frontier.luhnCheck)

    bench2[String, Int]("Mixed", "classifyCode")(Mixed.classifyCode)
    bench2[String, Double]("Mixed", "classifyFloat")(Mixed.classifyFloat)
    bench3[String, Int, Double]("Mixed", "triage")(Mixed.triage)
  }
}
