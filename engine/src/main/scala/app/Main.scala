package app

import app.Generators._
import benchmark.data.Tree
import benchmark.{Calibration, MagicLiterals, MixedTargets, MutationTargets, NumericSearch}
import pbt.Pbt
import pbt.gen.Generatable
import pbt.strategy.Strategy

import java.nio.file.Paths

object Main {

  private val SutRoot       = Paths.get("sut")
  private val ReportsBase   = Paths.get("engine/reports/statistics")
  private val DefaultInputs = 10000

  def main(args: Array[String]): Unit =
    (args.headOption.flatMap(Strategy.byName), args.lift(1).flatMap(_.toLongOption)) match {
      case (Some(strategy), Some(seed)) => runAll(strategy, seed, args.lift(2).flatMap(_.toIntOption).getOrElse(DefaultInputs))
      case _                            =>
        println("usage: engine/runMain app.Main <strategy> <seed> [inputs]")
        println(s"  strategies: ${Strategy.names.mkString(", ")}")
        sys.exit(1)
    }

  private def runAll(strategy: Strategy, seed: Long, inputs: Int): Unit = {
    val pbt = new Pbt(SutRoot)

    def bench[A: Generatable](category: String, method: String)(body: A => Any): Unit = {
      val source = Paths.get(s"sut/src/main/scala/benchmark/$category.scala")
      val out    = ReportsBase.resolve(category).resolve(method).resolve(strategy.name).resolve(f"seed=$seed%02d")
      pbt.check[A](source, method, strategy, seed, inputs)(in => { body(in); true }).write(out)
    }

    bench[Int]("Calibration", "sign")(Calibration.sign)
    bench[Option[Int]]("Calibration", "optionShape")(Calibration.optionShape)
    bench[List[Int]]("Calibration", "listShape")(Calibration.listShape)
    bench[(Int, Int)]("Calibration", "pairOrder") { case (x, y) => Calibration.pairOrder(x, y) }
    bench[Tree[Int]]("Calibration", "treeShape")(Calibration.treeShape)

    bench[Int]("MagicLiterals", "magicInt")(MagicLiterals.magicInt)
    bench[Option[Int]]("MagicLiterals", "magicOption")(MagicLiterals.magicOption)
    bench[(Int, Int)]("MagicLiterals", "coords") { case (x, y) => MagicLiterals.coords(x, y) }
    bench[List[Int]]("MagicLiterals", "whitelist")(MagicLiterals.whitelist)
    bench[Tree[Int]]("MagicLiterals", "treeMarker")(MagicLiterals.treeMarker)

    bench[List[Int]]("MutationTargets", "sortedLedger")(MutationTargets.sortedLedger)
    bench[Tree[Int]]("MutationTargets", "treeDepth")(MutationTargets.treeDepth)
    bench[List[Int]]("MutationTargets", "priceTrend")(MutationTargets.priceTrend)
    bench[List[Int]]("MutationTargets", "inventoryProfile")(MutationTargets.inventoryProfile)
    bench[(List[Int], List[Int])]("MutationTargets", "mergeJoinShape") { case (left, right) => MutationTargets.mergeJoinShape(left, right) }
    bench[(List[Int], Int)]("MutationTargets", "binarySearchRoute") { case (xs, target) => MutationTargets.binarySearchRoute(xs, target) }
    bench[Tree[Int]]("MutationTargets", "treeIndexProfile")(MutationTargets.treeIndexProfile)

    bench[(List[Int], Int)]("MixedTargets", "eventStream") { case (events, priority) => MixedTargets.eventStream(events, priority) }
    bench[(List[Int], List[Int], Int)]("MixedTargets", "reconciliation") { case (left, right, code) =>
      MixedTargets.reconciliation(left, right, code)
    }
    bench[(List[Int], Int)]("MixedTargets", "cacheProbe") { case (keys, hotKey) => MixedTargets.cacheProbe(keys, hotKey) }
    bench[(Tree[Int], Int)]("MixedTargets", "treeLookup") { case (t, key) => MixedTargets.treeLookup(t, key) }
    bench[(List[Int], Int, Int)]("MixedTargets", "batchWindow") { case (values, low, high) =>
      MixedTargets.batchWindow(values, low, high)
    }

    bench[Int]("NumericSearch", "window")(NumericSearch.window)
    bench[Int]("NumericSearch", "derivedEq")(NumericSearch.derivedEq)
    bench[Int]("NumericSearch", "offsetEq")(NumericSearch.offsetEq)
    bench[(Int, Int)]("NumericSearch", "band") { case (x, y) => NumericSearch.band(x, y) }
    bench[Int]("NumericSearch", "scaledOffset")(NumericSearch.scaledOffset)
  }
}
