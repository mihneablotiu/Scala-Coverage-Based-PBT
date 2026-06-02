package app

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.scalameta.ScalametaBranchTreeBuilder
import adapter.driven.scoverage.ScoverageSourceCoverageReader
import adapter.driving.fileSystem.FileSystemTestRunner
import app.Generators.tree // Generatable[Tree] — the user-type extension point
import benchmark._
import domain.{Generatable, Strategy}
import org.scalacheck.{rng, Test}
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Composition root. Runs every benchmark against one (strategy, seed) pair from the CLI.
  *
  * One JVM per (strategy, seed): scoverage's `Invoker` accumulates hits process-globally with no notion of a session, so each
  * `engine/runMain app.Main <strategy> <seed>` is forked (`fork := true`). The Makefile sweeps STRATEGIES × SEEDS. Reports land under
  * `engine/reports/statistics/<category>/<method>/<strategy>/seed=<NN>/`.
  */
object Main {

  private val SutRoot: Path     = Paths.get("sut")
  private val ReportsBase: Path = Paths.get("engine/reports/statistics")
  private val Inputs            = 10000

  private def src(category: String): Path = Paths.get(s"sut/src/main/scala/benchmark/$category.scala")

  def main(args: Array[String]): Unit = {
    val strategy = args.headOption.filter(Strategy.names.contains)
    val seed     = args.lift(1).flatMap(_.toLongOption)
    (strategy, seed) match {
      case (Some(name), Some(s)) => runAll(name, s)
      case _                     =>
        println("usage: engine/runMain app.Main <strategy> <seed>")
        println(s"  strategies: ${Strategy.names.mkString(", ")}")
        println("  seed:       any signed Long (the Makefile sweeps SEEDS=1..10)")
        sys.exit(1)
    }
  }

  /** Branch-distance objectives for the `coverage-guided` strategy, keyed by method name. Each is a hand-written distance to a hard,
    * currently-uncovered leaf (0 = reached) — Löscher-style targeting. Methods without an entry fall back to plain random under `coverage-guided`.
    */
  private val Objectives: Map[String, Any] = Map(
    // tightBand: reach the [1000, 1009] band.
    "tightBand" -> ((n: Int) => math.max(0.0, math.max(1000.0 - n, n - 1009.0))),
    // compareInts: reach "big-negatives" (|a| > 1000 and a == -b).
    "compareInts" -> ((p: (Int, Int)) => { val (a, b) = p; math.max(0.0, 1001.0 - math.abs(a.toLong)) + math.abs(a.toLong + b.toLong) }),
    // triangleType: reach "isosceles" (the arm random misses) — drive toward (a == b >= 2, c == 1),
    // e.g. (2, 2, 1): a valid, non-equilateral triangle with two equal sides.
    "triangleType" -> ((t: (Int, Int, Int)) => {
      val (a, b, c) = t
      math.abs(a.toLong - b).toDouble + math.max(0L, 2L - a) + math.abs(c.toLong - 1)
    }),
    // isStrictlySorted: reach a strictly-sorted list of length >= 8 (structural — a harder climb).
    "isStrictlySorted" -> ((xs: List[Int]) =>
      if (xs.size < 8) (8 - xs.size).toDouble else xs.lazyZip(xs.tail).count { case (a, b) => a >= b }.toDouble
    )
  )

  private def runAll(strategyName: String, seed: Long): Unit = {
    val params  = Test.Parameters.default.withInitialSeed(rng.Seed(seed)).withMinSuccessfulTests(Inputs)
    val handler = new TestRunnerHandler(
      treeBuilder = ScalametaBranchTreeBuilder(),
      sourceCoverage = ScoverageSourceCoverageReader(SutRoot),
      writer = FileSystemCoverageReportWriter(),
      params = params,
      objectives = Objectives
    )
    val seedLabel                     = f"seed=$seed%02d"
    def runner(c: String): TestRunner = new FileSystemTestRunner(handler, src(c), ReportsBase, Some(seedLabel))

    // The SUT methods return String/Boolean; the engine measures coverage regardless of the verdict,
    // so the property body just exercises the method and returns true.
    def bench[A: Generatable](r: TestRunner, name: String)(body: A => Any): Unit =
      r.runTests[A](name, strategyName)(in => { body(in); true })

    val saturated = runner("Saturated")
    bench(saturated, "sign")(Saturated.sign)
    bench(saturated, "headSign")(Saturated.headSign)

    val magic = runner("MagicConstants")
    bench(magic, "classify")(MagicConstants.classify)
    bench(magic, "accessLevel")(MagicConstants.accessLevel)
    bench(magic, "luckyLong")(MagicConstants.luckyLong)
    bench(magic, "routeOption")(MagicConstants.routeOption)
    bench(magic, "lookupRole")(MagicConstants.lookupRole)
    bench[(Boolean, Int)](magic, "gatedMagic")((MagicConstants.gatedMagic _).tupled)

    val narrow = runner("NarrowRanges")
    bench(narrow, "tightBand")(NarrowRanges.tightBand)
    bench(narrow, "longBand")(NarrowRanges.longBand)
    bench(narrow, "magnitude")(NarrowRanges.magnitude)
    bench(narrow, "nearPi")(NarrowRanges.nearPi)

    val relational = runner("Relational")
    bench[(Int, Int)](relational, "compareInts")((Relational.compareInts _).tupled)
    bench[(List[Int], List[Int])](relational, "isReverseOf")((Relational.isReverseOf _).tupled)
    bench[(Map[String, Int], Map[String, Int])](relational, "sameKeys")((Relational.sameKeys _).tupled)

    val structural = runner("StructuralInvariants")
    bench(structural, "isStrictlySorted")(StructuralInvariants.isStrictlySorted)
    bench(structural, "runLengthShape")(StructuralInvariants.runLengthShape)
    bench(structural, "bstShape")(StructuralInvariants.bstShape)

    val deep = runner("DeepConditionals")
    bench[(Int, Int, Int)](deep, "triangleType")((DeepConditionals.triangleType _).tupled)
    bench(deep, "deepClassify")(DeepConditionals.deepClassify)
    bench(deep, "gridShape")(DeepConditionals.gridShape)
    bench[(Int, String, List[Int])](deep, "mixedClassify")((DeepConditionals.mixedClassify _).tupled)

    val staged = runner("StagedValidity")
    bench(staged, "parseVersion")(StagedValidity.parseVersion)
    bench(staged, "parseSignedInt")(StagedValidity.parseSignedInt)
    bench[(List[Int], Int)](staged, "elementAt")((StagedValidity.elementAt _).tupled)
    bench(staged, "balancedBrackets")(StagedValidity.balancedBrackets)
    bench(staged, "luhnCheck")(StagedValidity.luhnCheck)
  }
}
