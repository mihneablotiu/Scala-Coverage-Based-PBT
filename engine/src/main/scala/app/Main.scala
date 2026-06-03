package app

import app.Generators.tree // Generatable[Tree] — a user-type instance, the extension point
import benchmark._
import pbt.gen.Generatable
import pbt.strategy.Strategy
import pbt.{Coverage, Pbt}

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
    val coverage = new Coverage(SutRoot)

    // The SUT methods return String/Boolean; coverage is measured regardless of the verdict, so the property just exercises the method.
    def bench[A: Generatable](category: String, method: String)(body: A => Any): Unit =
      Pbt
        .check[A](source(category), method, strategy, coverage, seed, Inputs)(in => { body(in); true })
        .write(ReportsBase.resolve(category).resolve(method).resolve(strategy.name).resolve(f"seed=$seed%02d"))

    bench[Int]("Saturated", "sign")(Saturated.sign)
    bench[List[Int]]("Saturated", "headSign")(Saturated.headSign)

    bench[Int]("MagicConstants", "classify")(MagicConstants.classify)
    bench[String]("MagicConstants", "accessLevel")(MagicConstants.accessLevel)
    bench[Long]("MagicConstants", "luckyLong")(MagicConstants.luckyLong)
    bench[Option[Int]]("MagicConstants", "routeOption")(MagicConstants.routeOption)
    bench[Map[String, Int]]("MagicConstants", "lookupRole")(MagicConstants.lookupRole)
    bench[(Boolean, Int)]("MagicConstants", "gatedMagic")((MagicConstants.gatedMagic _).tupled)

    bench[Int]("NarrowRanges", "tightBand")(NarrowRanges.tightBand)
    bench[Long]("NarrowRanges", "longBand")(NarrowRanges.longBand)
    bench[Double]("NarrowRanges", "magnitude")(NarrowRanges.magnitude)
    bench[Double]("NarrowRanges", "nearPi")(NarrowRanges.nearPi)

    bench[(Int, Int)]("Relational", "compareInts")((Relational.compareInts _).tupled)
    bench[(List[Int], List[Int])]("Relational", "isReverseOf")((Relational.isReverseOf _).tupled)
    bench[(Map[String, Int], Map[String, Int])]("Relational", "sameKeys")((Relational.sameKeys _).tupled)

    bench[List[Int]]("StructuralInvariants", "isStrictlySorted")(StructuralInvariants.isStrictlySorted)
    bench[List[Int]]("StructuralInvariants", "runLengthShape")(StructuralInvariants.runLengthShape)
    bench[benchmark.data.Tree]("StructuralInvariants", "bstShape")(StructuralInvariants.bstShape)

    bench[(Int, Int, Int)]("DeepConditionals", "triangleType")((DeepConditionals.triangleType _).tupled)
    bench[Int]("DeepConditionals", "deepClassify")(DeepConditionals.deepClassify)
    bench[List[List[Int]]]("DeepConditionals", "gridShape")(DeepConditionals.gridShape)
    bench[(Int, String, List[Int])]("DeepConditionals", "mixedClassify")((DeepConditionals.mixedClassify _).tupled)

    bench[String]("StagedValidity", "parseVersion")(StagedValidity.parseVersion)
    bench[String]("StagedValidity", "parseSignedInt")(StagedValidity.parseSignedInt)
    bench[(List[Int], Int)]("StagedValidity", "elementAt")((StagedValidity.elementAt _).tupled)
    bench[String]("StagedValidity", "balancedBrackets")(StagedValidity.balancedBrackets)
    bench[List[Int]]("StagedValidity", "luhnCheck")(StagedValidity.luhnCheck)
  }
}
