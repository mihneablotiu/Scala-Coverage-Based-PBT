package app

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.scalameta.ScalametaBranchTreeBuilder
import adapter.driven.scoverage.ScoverageSourceCoverageReader
import adapter.driving.fileSystem.FileSystemTestRunner
import benchmark.bool.BoolBench
import benchmark.int.IntBench
import benchmark.list.ListBench
import cats.effect.{IO, IOApp}
import domain.Strategy
import org.scalacheck.{rng, Arbitrary, Test}
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Composition root. Wires driven adapters, builds one handler, instantiates one driving adapter
  * per SUT source file, and dispatches each benchmark.
  *
  * Reports are written under `engine/reports/<SourceFileStem>/<methodName>/...` thanks to the
  * driving adapter resolving the source-file stem (e.g. `IntBench`) as the first segment.
  *
  * Multi-parameter SUT methods are dispatched as `bench[(A, B, ...)]` with `(method _).tupled`.
  * Benchmarks are ordered top → bottom by depth and number of unreached arms.
  */
object Main extends IOApp.Simple {

  private val SutRoot: Path = Paths.get("sut")
  private val ReportsBase: Path = Paths.get("engine/reports")
  private val BoolSrc: Path = Paths.get("sut/src/main/scala/benchmark/bool/BoolBench.scala")
  private val IntSrc: Path = Paths.get("sut/src/main/scala/benchmark/int/IntBench.scala")
  private val ListSrc: Path = Paths.get("sut/src/main/scala/benchmark/list/ListBench.scala")

  private val testParams: Test.Parameters =
    Test.Parameters.default.withInitialSeed(rng.Seed(0L))

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

  /** Run a benchmark: call the SUT method on each generated input, ignore the return value. */
  private def bench[A: Arbitrary](
      runner: TestRunner,
      name: String,
      strategy: Strategy
  )(body: A => Any): IO[Unit] =
    runner.runTests[A](name, strategy)(a => { body(a); true })

  override val run: IO[Unit] = for {
    // BoolBench — trivial baselines on the smallest input type.
    _ <- bench(bools, "identity", Strategy.Random)(BoolBench.identity)
    _ <- bench(bools, "negate", Strategy.Random)(BoolBench.negate)
    _ <- bench[(Boolean, Boolean, Boolean)](bools, "threeAgree", Strategy.Random)(
      (BoolBench.threeAgree _).tupled
    )

    // IntBench — saturated → 1 unreached → 2-3 unreached → 4+ unreached.
    _ <- bench(ints, "isPositive", Strategy.Random)(IntBench.isPositive)
    _ <- bench(ints, "parity", Strategy.Random)(IntBench.parity)
    _ <- bench(ints, "sign", Strategy.Random)(IntBench.sign)
    _ <- bench(ints, "mod97", Strategy.Random)(IntBench.mod97)
    _ <- bench(ints, "isPalindromeNumber", Strategy.Random)(IntBench.isPalindromeNumber)
    _ <- bench(ints, "classify", Strategy.Random)(IntBench.classify)
    _ <- bench(ints, "divisibleByThousand", Strategy.Random)(IntBench.divisibleByThousand)
    _ <- bench(ints, "signedPerfectSquare", Strategy.Random)(IntBench.signedPerfectSquare)
    _ <- bench(ints, "parityPlusSquare", Strategy.Random)(IntBench.parityPlusSquare)
    _ <- bench[(Int, Int)](ints, "divisibilityRelation", Strategy.Random)(
      (IntBench.divisibilityRelation _).tupled
    )
    _ <- bench(ints, "signedPalindrome", Strategy.Random)(IntBench.signedPalindrome)
    _ <- bench(ints, "magicNumbers", Strategy.Random)(IntBench.magicNumbers)
    _ <- bench(ints, "isPrime", Strategy.Random)(IntBench.isPrime)
    _ <- bench(ints, "isFibonacci", Strategy.Random)(IntBench.isFibonacci)
    _ <- bench(ints, "collatzClass", Strategy.Random)(IntBench.collatzClass)
    _ <- bench[(Int, Int, Int)](ints, "triangleType", Strategy.Random)(
      (IntBench.triangleType _).tupled
    )
    _ <- bench(ints, "deepIntClassify", Strategy.Random)(IntBench.deepIntClassify)
    _ <- bench[(Int, Int, Int)](ints, "deepIntTriple", Strategy.Random)(
      (IntBench.deepIntTriple _).tupled
    )

    // ListBench — saturated → 1 unreached → 2-3 unreached → 4+ unreached.
    _ <- bench(lists, "isEmpty", Strategy.Random)(ListBench.isEmpty)
    _ <- bench(lists, "sizeClass", Strategy.Random)(ListBench.sizeClass)
    _ <- bench(lists, "allSameSign", Strategy.Random)(ListBench.allSameSign)
    _ <- bench[(List[Int], List[Int])](lists, "lengthCompare", Strategy.Random)(
      (ListBench.lengthCompare _).tupled
    )
    _ <- bench(lists, "sumClass", Strategy.Random)(ListBench.sumClass)
    _ <- bench(lists, "isStrictlySorted", Strategy.Random)(ListBench.isStrictlySorted)
    _ <- bench(lists, "allEqual", Strategy.Random)(ListBench.allEqual)
    _ <- bench(lists, "extremesGap", Strategy.Random)(ListBench.extremesGap)
    _ <- bench(lists, "palindromeClass", Strategy.Random)(ListBench.palindromeClass)
    _ <- bench(lists, "listMaxMin", Strategy.Random)(ListBench.listMaxMin)
    _ <- bench[(List[Int], List[Int])](lists, "prefixCheck", Strategy.Random)(
      (ListBench.prefixCheck _).tupled
    )
    _ <- bench[(List[Int], List[Int])](lists, "isReverseOf", Strategy.Random)(
      (ListBench.isReverseOf _).tupled
    )
    _ <- bench[(List[Int], List[Int])](lists, "haveSameMultiset", Strategy.Random)(
      (ListBench.haveSameMultiset _).tupled
    )
    _ <- bench(lists, "allPrime", Strategy.Random)(ListBench.allPrime)
    _ <- bench(lists, "primeListShape", Strategy.Random)(ListBench.primeListShape)
    _ <- bench(lists, "isPermutation", Strategy.Random)(ListBench.isPermutation)
    _ <- bench[(List[Int], List[Int])](lists, "deepListRelation", Strategy.Random)(
      (ListBench.deepListRelation _).tupled
    )
    _ <- bench(lists, "deepListShape", Strategy.Random)(ListBench.deepListShape)
  } yield ()
}
