package app

import app.Generators._
import benchmark.Saturated
import pbt.Pbt
import pbt.gen.Generatable
import pbt.strategy.Strategy

import java.nio.file.Paths

/** The experiment harness: runs every benchmark method against one (strategy, seed) pair, then writes one report per method. One forked JVM per pair
  * (scoverage's `Invoker` is process-global, so a shared JVM would leak coverage between runs). A real user runs a single property with one
  * [[Pbt.check]] call; this just drives the whole catalogue.
  */
object Main {

  private val SutRoot     = Paths.get("sut")
  private val ReportsBase = Paths.get("engine/reports/statistics")
  private val Inputs      = 10000

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

    // Coverage is measured regardless of the verdict, so the property only needs to exercise the method.
    def bench[A: Generatable](category: String, method: String)(body: A => Any): Unit = {
      val source = Paths.get(s"sut/src/main/scala/benchmark/$category.scala")
      val out    = ReportsBase.resolve(category).resolve(method).resolve(strategy.name).resolve(f"seed=$seed%02d")
      pbt.check[A](source, method, strategy, seed, Inputs)(in => { body(in); true }).write(out)
    }

    bench[Int]("Saturated", "sign")(Saturated.sign)
    bench[List[Int]]("Saturated", "headSign")(Saturated.headSign)
    bench[Option[Int]]("Saturated", "size")(Saturated.size)
  }
}
