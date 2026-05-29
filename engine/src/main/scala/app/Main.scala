package app

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.scalameta.ScalametaBranchTreeBuilder
import adapter.driven.scoverage.ScoverageSourceCoverageReader
import adapter.driving.fileSystem.FileSystemTestRunner
import benchmark.bool.BoolBench
import benchmark.int.IntBench
import benchmark.list.ListBench
import cats.effect.{ExitCode, IO, IOApp}
import domain.{Mutator, Strategy}
import org.scalacheck.{rng, Arbitrary, Test}
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Composition root. Runs every benchmark against one strategy picked from the first CLI arg.
  *
  * One JVM per strategy: scoverage's `Invoker` accumulates statement hits within a JVM and has no notion of a session, so each
  * `engine/runMain app.Main <strategy>` is forked (`fork := true` in build.sbt) to isolate strategies. Per-method scoping inside a JVM is enforced by
  * `ScoverageSourceCoverageReader.coverage` filtering by `(sourceFile, methodName)`. The multi-strategy sweep lives in the Makefile.
  *
  * Reports land under `engine/reports/statistics/<sourceStem>/<methodName>/<strategy.name>/`.
  */
object Main extends IOApp {

  private val SutRoot: Path     = Paths.get("sut")
  private val ReportsBase: Path = Paths.get("engine/reports/statistics")
  private val BoolSrc: Path     = Paths.get("sut/src/main/scala/benchmark/bool/BoolBench.scala")
  private val IntSrc: Path      = Paths.get("sut/src/main/scala/benchmark/int/IntBench.scala")
  private val ListSrc: Path     = Paths.get("sut/src/main/scala/benchmark/list/ListBench.scala")

  /** 1000 inputs, seed `0L` — explicit so reproducibility doesn't ride on ScalaCheck defaults. */
  private val testParams: Test.Parameters =
    Test.Parameters.default
      .withInitialSeed(rng.Seed(0L))
      .withMinSuccessfulTests(1000)

  private val handler: TestRunnerHandler = new TestRunnerHandler(
    treeBuilder = ScalametaBranchTreeBuilder(),
    sourceCoverage = ScoverageSourceCoverageReader(SutRoot),
    writer = FileSystemCoverageReportWriter(),
    params = testParams
  )

  private val bools: TestRunner = new FileSystemTestRunner(handler, BoolSrc, ReportsBase)
  private val ints: TestRunner  = new FileSystemTestRunner(handler, IntSrc, ReportsBase)
  private val lists: TestRunner = new FileSystemTestRunner(handler, ListSrc, ReportsBase)

  override def run(args: List[String]): IO[ExitCode] =
    args.headOption.filter(Strategy.names.contains) match {
      case Some(name) => runAll(name).as(ExitCode.Success)
      case None       => usage.as(ExitCode.Error)
    }

  private val usage: IO[Unit] =
    IO.println(
      s"usage: engine/runMain app.Main <strategy>   (available: ${Strategy.names.mkString(", ")})"
    )

  /** The SUT methods return `String`/`Int`/etc., not `Boolean`; the engine port wants `A => Boolean`. `bench` adapts at the boundary so the SUT
    * itself stays predicate-free — a future client with a real property would just pass an `A => Boolean` directly.
    */
  private def runAll(strategyName: String): IO[Unit] = {
    def bench[A: Arbitrary: Mutator](runner: TestRunner, name: String)(body: A => Any): IO[Unit] =
      runner.runTests(name, Strategy.parse[A](strategyName).get)(input => { body(input); true })

    for {
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
