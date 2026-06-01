package app

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.scalameta.ScalametaBranchTreeBuilder
import adapter.driven.scoverage.ScoverageSourceCoverageReader
import adapter.driving.fileSystem.FileSystemTestRunner
import benchmark.bool.BoolBench
import benchmark.int.IntBench
import benchmark.list.ListBench
import domain.{Generatable, Strategy}
import org.scalacheck.{rng, Test}
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Composition root. Runs every benchmark against one (strategy, seed) pair picked from the CLI args.
  *
  * One JVM per (strategy, seed): scoverage's `Invoker` accumulates statement hits within a JVM and has no notion of a session, so each
  * `engine/runMain app.Main <strategy> <seed>` is forked (`fork := true` in build.sbt) to isolate runs. The Makefile sweeps both dimensions in series
  * (`STRATEGIES × SEEDS`).
  *
  * Reports land under `engine/reports/statistics/<sourceStem>/<methodName>/<strategy.name>/seed=<NN>/`.
  */
object Main {

  private val SutRoot: Path     = Paths.get("sut")
  private val ReportsBase: Path = Paths.get("engine/reports/statistics")
  private val BoolSrc: Path     = Paths.get("sut/src/main/scala/benchmark/bool/BoolBench.scala")
  private val IntSrc: Path      = Paths.get("sut/src/main/scala/benchmark/int/IntBench.scala")
  private val ListSrc: Path     = Paths.get("sut/src/main/scala/benchmark/list/ListBench.scala")

  def main(args: Array[String]): Unit = {
    val maybeStrategy = args.headOption.filter(Strategy.names.contains)
    val maybeSeed     = args.lift(1).flatMap(_.toLongOption)
    (maybeStrategy, maybeSeed) match {
      case (Some(name), Some(seed)) => runAll(name, seed)
      case _                        =>
        val strategies = Strategy.names.mkString(", ")
        println(s"usage: engine/runMain app.Main <strategy> <seed>")
        println(s"  strategies: $strategies")
        println(s"  seed:       any signed Long (the Makefile sweeps SEEDS=1..10)")
        sys.exit(1)
    }
  }

  /** The SUT methods return `String`/`Int`/etc., not `Boolean`; the engine port wants `A => Boolean`. `bench` adapts at the boundary so the SUT
    * itself stays predicate-free — a future client with a real property would just pass an `A => Boolean` directly.
    *
    * 10000 inputs per (strategy, seed); the initial seed comes from the CLI so that the Makefile can sweep K seeds in series and downstream analysis
    * sees a `seed=NN/` segment in each cell's output path.
    */
  private def runAll(strategyName: String, seed: Long): Unit = {
    val testParams: Test.Parameters =
      Test.Parameters.default
        .withInitialSeed(rng.Seed(seed))
        .withMinSuccessfulTests(10000)

    val handler: TestRunnerHandler = new TestRunnerHandler(
      treeBuilder = ScalametaBranchTreeBuilder(),
      sourceCoverage = ScoverageSourceCoverageReader(SutRoot),
      writer = FileSystemCoverageReportWriter(),
      params = testParams
    )

    val seedLabel         = f"seed=$seed%02d"
    val bools: TestRunner = new FileSystemTestRunner(handler, BoolSrc, ReportsBase, Some(seedLabel))
    val ints: TestRunner  = new FileSystemTestRunner(handler, IntSrc, ReportsBase, Some(seedLabel))
    val lists: TestRunner = new FileSystemTestRunner(handler, ListSrc, ReportsBase, Some(seedLabel))

    def bench[A: Generatable](runner: TestRunner, name: String)(body: A => Any): Unit =
      runner.runTests[A](name, strategyName)(input => { body(input); true })

    bench(bools, "negate")(BoolBench.negate)
    bench[(Boolean, Boolean, Boolean)](bools, "threeAgree")((BoolBench.threeAgree _).tupled)

    bench(ints, "isPositive")(IntBench.isPositive)
    bench(ints, "parity")(IntBench.parity)
    bench(ints, "sign")(IntBench.sign)
    bench(ints, "mod97")(IntBench.mod97)
    bench(ints, "isPalindromeNumber")(IntBench.isPalindromeNumber)
    bench(ints, "classify")(IntBench.classify)
    bench(ints, "divisibleByThousand")(IntBench.divisibleByThousand)
    bench(ints, "signedPerfectSquare")(IntBench.signedPerfectSquare)
    bench(ints, "parityPlusSquare")(IntBench.parityPlusSquare)
    bench[(Int, Int)](ints, "divisibilityRelation")((IntBench.divisibilityRelation _).tupled)
    bench(ints, "signedPalindrome")(IntBench.signedPalindrome)
    bench(ints, "magicNumbers")(IntBench.magicNumbers)
    bench(ints, "isPrime")(IntBench.isPrime)
    bench(ints, "isFibonacci")(IntBench.isFibonacci)
    bench(ints, "collatzClass")(IntBench.collatzClass)
    bench[(Int, Int, Int)](ints, "triangleType")((IntBench.triangleType _).tupled)
    bench(ints, "deepIntClassify")(IntBench.deepIntClassify)
    bench[(Int, Int, Int)](ints, "deepIntTriple")((IntBench.deepIntTriple _).tupled)

    bench(lists, "isEmpty")(ListBench.isEmpty)
    bench(lists, "sizeClass")(ListBench.sizeClass)
    bench(lists, "allSameSign")(ListBench.allSameSign)
    bench[(List[Int], List[Int])](lists, "lengthCompare")((ListBench.lengthCompare _).tupled)
    bench(lists, "sumClass")(ListBench.sumClass)
    bench(lists, "isStrictlySorted")(ListBench.isStrictlySorted)
    bench(lists, "allEqual")(ListBench.allEqual)
    bench(lists, "extremesGap")(ListBench.extremesGap)
    bench(lists, "palindromeClass")(ListBench.palindromeClass)
    bench(lists, "listMaxMin")(ListBench.listMaxMin)
    bench[(List[Int], List[Int])](lists, "prefixCheck")((ListBench.prefixCheck _).tupled)
    bench[(List[Int], List[Int])](lists, "isReverseOf")((ListBench.isReverseOf _).tupled)
    bench[(List[Int], List[Int])](lists, "haveSameMultiset")((ListBench.haveSameMultiset _).tupled)
    bench(lists, "allPrime")(ListBench.allPrime)
    bench(lists, "primeListShape")(ListBench.primeListShape)
    bench(lists, "isPermutation")(ListBench.isPermutation)
    bench[(List[Int], List[Int])](lists, "deepListRelation")((ListBench.deepListRelation _).tupled)
    bench(lists, "deepListShape")(ListBench.deepListShape)
  }
}
