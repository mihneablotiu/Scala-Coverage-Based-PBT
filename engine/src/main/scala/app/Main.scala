package app

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.scalameta.ScalametaBranchTreeBuilder
import adapter.driven.scoverage.ScoverageSourceCoverageReader
import adapter.driving.fileSystem.FileSystemTestRunner
import benchmark.bool.BoolBench
import benchmark.int.IntBench
import benchmark.list.ListBench
import cats.effect.{ExitCode, IO, IOApp}
import domain.Strategy
import org.scalacheck.{rng, Arbitrary, Test}
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Composition root. Runs every benchmark against **one** strategy, picked from the first CLI arg.
  *
  * One-JVM-per-strategy is the design choice that keeps the scoverage runtime — which has no notion
  * of a session and accumulates statement hits within a JVM — from leaking one strategy's coverage
  * into the next. Each `engine/runMain app.Main <strategy>` invocation forks a fresh JVM (see
  * `fork := true` in `build.sbt`), so the scoverage `Invoker` only ever holds the hits of this
  * single strategy. Within that JVM, scoverage still accumulates across every benchmark; the
  * per-benchmark report stays method-scoped because `ScoverageSourceCoverageReader.methodCoverage`
  * filters statements by `(sourceFile, methodName)`. Multi-strategy orchestration lives in the
  * Makefile.
  *
  * Reports are written under `engine/reports/<SourceStem>/<methodName>/<strategy.name>/...` — each
  * (method, strategy) pair owns its own folder so the strategy outputs sit side-by-side.
  *
  * Multi-parameter SUT methods are dispatched with `(method _).tupled`. Benchmarks are ordered top
  * → bottom by depth and number of unreached arms.
  */
object Main extends IOApp {

  private val SutRoot: Path = Paths.get("sut")
  private val ReportsBase: Path = Paths.get("engine/reports")
  private val BoolSrc: Path = Paths.get("sut/src/main/scala/benchmark/bool/BoolBench.scala")
  private val IntSrc: Path = Paths.get("sut/src/main/scala/benchmark/int/IntBench.scala")
  private val ListSrc: Path = Paths.get("sut/src/main/scala/benchmark/list/ListBench.scala")

  /** 100 inputs / seed `0L` — match what the docs claim and survive any future ScalaCheck default
    * change. Per-strategy reproducibility hinges on these two knobs.
    */
  private val testParams: Test.Parameters =
    Test.Parameters.default
      .withInitialSeed(rng.Seed(0L))
      .withMinSuccessfulTests(100)

  private val sourceCoverage = ScoverageSourceCoverageReader(SutRoot)

  private val handler: TestRunnerHandler = new TestRunnerHandler(
    treeBuilder = ScalametaBranchTreeBuilder(),
    sourceCoverage = sourceCoverage,
    writer = FileSystemCoverageReportWriter(),
    params = testParams
  )

  private val bools: TestRunner = new FileSystemTestRunner(handler, BoolSrc, ReportsBase)
  private val ints: TestRunner = new FileSystemTestRunner(handler, IntSrc, ReportsBase)
  private val lists: TestRunner = new FileSystemTestRunner(handler, ListSrc, ReportsBase)

  override def run(args: List[String]): IO[ExitCode] =
    args.headOption.flatMap(Strategy.parse) match {
      case Some(strategy) => runAll(strategy).as(ExitCode.Success)
      case None           => usage.as(ExitCode.Error)
    }

  private val usage: IO[Unit] = {
    val available = Strategy.all.map(_.name).mkString(", ")
    IO.println(s"usage: engine/runMain app.Main <strategy>   (available: $available)")
  }

  /** Body of one JVM: run every benchmark against the given strategy. `bench` closes over the
    * strategy so each call site reads as a single benchmark line.
    */
  private def runAll(strategy: Strategy): IO[Unit] = {
    def bench[A: Arbitrary](runner: TestRunner, name: String)(body: A => Any): IO[Unit] =
      runner.runTests[A](name, strategy)(a => { body(a); true })

    for {
      _ <- bench(bools, "identity")(BoolBench.identity)
      _ <- bench(bools, "negate")(BoolBench.negate)
      _ <- bench[(Boolean, Boolean, Boolean)](bools, "threeAgree")((BoolBench.threeAgree _).tupled)

      _ <- bench(ints, "isPositive")(IntBench.isPositive)
      _ <- bench(ints, "parity")(IntBench.parity)
      _ <- bench(ints, "sign")(IntBench.sign)
      _ <- bench(ints, "mod97")(IntBench.mod97)
      _ <- bench(ints, "isPalindromeNumber")(IntBench.isPalindromeNumber)
      _ <- bench(ints, "classify")(IntBench.classify)
      _ <- bench(ints, "divisibleByThousand")(IntBench.divisibleByThousand)
      _ <- bench(ints, "signedPerfectSquare")(IntBench.signedPerfectSquare)
      _ <- bench(ints, "parityPlusSquare")(IntBench.parityPlusSquare)
      _ <- bench[(Int, Int)](ints, "divisibilityRelation")((IntBench.divisibilityRelation _).tupled)
      _ <- bench(ints, "signedPalindrome")(IntBench.signedPalindrome)
      _ <- bench(ints, "magicNumbers")(IntBench.magicNumbers)
      _ <- bench(ints, "isPrime")(IntBench.isPrime)
      _ <- bench(ints, "isFibonacci")(IntBench.isFibonacci)
      _ <- bench(ints, "collatzClass")(IntBench.collatzClass)
      _ <- bench[(Int, Int, Int)](ints, "triangleType")((IntBench.triangleType _).tupled)
      _ <- bench(ints, "deepIntClassify")(IntBench.deepIntClassify)
      _ <- bench[(Int, Int, Int)](ints, "deepIntTriple")((IntBench.deepIntTriple _).tupled)

      _ <- bench(lists, "isEmpty")(ListBench.isEmpty)
      _ <- bench(lists, "sizeClass")(ListBench.sizeClass)
      _ <- bench(lists, "allSameSign")(ListBench.allSameSign)
      _ <- bench[(List[Int], List[Int])](lists, "lengthCompare")((ListBench.lengthCompare _).tupled)
      _ <- bench(lists, "sumClass")(ListBench.sumClass)
      _ <- bench(lists, "isStrictlySorted")(ListBench.isStrictlySorted)
      _ <- bench(lists, "allEqual")(ListBench.allEqual)
      _ <- bench(lists, "extremesGap")(ListBench.extremesGap)
      _ <- bench(lists, "palindromeClass")(ListBench.palindromeClass)
      _ <- bench(lists, "listMaxMin")(ListBench.listMaxMin)
      _ <- bench[(List[Int], List[Int])](lists, "prefixCheck")((ListBench.prefixCheck _).tupled)
      _ <- bench[(List[Int], List[Int])](lists, "isReverseOf")((ListBench.isReverseOf _).tupled)
      _ <- bench[(List[Int], List[Int])](lists, "haveSameMultiset")(
             (ListBench.haveSameMultiset _).tupled
           )
      _ <- bench(lists, "allPrime")(ListBench.allPrime)
      _ <- bench(lists, "primeListShape")(ListBench.primeListShape)
      _ <- bench(lists, "isPermutation")(ListBench.isPermutation)
      _ <- bench[(List[Int], List[Int])](lists, "deepListRelation")(
             (ListBench.deepListRelation _).tupled
           )
      _ <- bench(lists, "deepListShape")(ListBench.deepListShape)
    } yield ()
  }
}
