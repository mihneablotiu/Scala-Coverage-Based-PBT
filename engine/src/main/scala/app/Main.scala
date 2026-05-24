package app

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.scalameta.ScalametaBranchTreeBuilder
import adapter.driven.scoverage.ScoverageSourceCoverageReader
import adapter.driving.fileSystem.FileSystemTestRunner
import benchmark.bool.BoolBench
import benchmark.int.IntBench
import benchmark.list.ListBench
import cats.effect.{IO, IOApp}
import cats.syntax.foldable._
import domain.Strategy
import org.scalacheck.{rng, Arbitrary, Test}
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Composition root. Wires driven adapters, builds one handler, instantiates one driving adapter
  * per SUT source file, and dispatches each benchmark against *every* configured strategy.
  *
  * Reports are written under `engine/reports/<SourceStem>/<methodName>/<strategy.name>/...` —
  * each (method, strategy) pair owns its own folder so the strategy outputs sit side-by-side.
  *
  * Multi-parameter SUT methods are dispatched as `benchAll[(A, B, ...)]` with `(method _).tupled`.
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

  /** The strategies every benchmark gets run against. Adding one here automatically runs every
    * benchmark under that strategy too — its outputs land in a sibling folder of the existing
    * `random/` etc.
    */
  private val strategies: List[Strategy] = List(
    Strategy.Random,
    Strategy.MutationGuided,
    Strategy.FeedbackBiasGuided
  )

  /** Run a benchmark against one strategy: call the SUT method on each generated input, ignore
    * the return value.
    */
  private def bench[A: Arbitrary](
      runner: TestRunner,
      name: String,
      strategy: Strategy
  )(body: A => Any): IO[Unit] =
    runner.runTests[A](name, strategy)(a => { body(a); true })

  /** Run a benchmark against every configured strategy, in order. */
  private def benchAll[A: Arbitrary](
      runner: TestRunner,
      name: String
  )(body: A => Any): IO[Unit] =
    strategies.traverse_(s => bench(runner, name, s)(body))

  override val run: IO[Unit] = for {
    // BoolBench — trivial baselines on the smallest input type.
    _ <- benchAll(bools, "identity")(BoolBench.identity)
    _ <- benchAll(bools, "negate")(BoolBench.negate)
    _ <- benchAll[(Boolean, Boolean, Boolean)](bools, "threeAgree")(
      (BoolBench.threeAgree _).tupled
    )

    // IntBench — saturated → 1 unreached → 2-3 unreached → 4+ unreached.
    _ <- benchAll(ints, "isPositive")(IntBench.isPositive)
    _ <- benchAll(ints, "parity")(IntBench.parity)
    _ <- benchAll(ints, "sign")(IntBench.sign)
    _ <- benchAll(ints, "mod97")(IntBench.mod97)
    _ <- benchAll(ints, "isPalindromeNumber")(IntBench.isPalindromeNumber)
    _ <- benchAll(ints, "classify")(IntBench.classify)
    _ <- benchAll(ints, "divisibleByThousand")(IntBench.divisibleByThousand)
    _ <- benchAll(ints, "signedPerfectSquare")(IntBench.signedPerfectSquare)
    _ <- benchAll(ints, "parityPlusSquare")(IntBench.parityPlusSquare)
    _ <- benchAll[(Int, Int)](ints, "divisibilityRelation")(
      (IntBench.divisibilityRelation _).tupled
    )
    _ <- benchAll(ints, "signedPalindrome")(IntBench.signedPalindrome)
    _ <- benchAll(ints, "magicNumbers")(IntBench.magicNumbers)
    _ <- benchAll(ints, "isPrime")(IntBench.isPrime)
    _ <- benchAll(ints, "isFibonacci")(IntBench.isFibonacci)
    _ <- benchAll(ints, "collatzClass")(IntBench.collatzClass)
    _ <- benchAll[(Int, Int, Int)](ints, "triangleType")(
      (IntBench.triangleType _).tupled
    )
    _ <- benchAll(ints, "deepIntClassify")(IntBench.deepIntClassify)
    _ <- benchAll[(Int, Int, Int)](ints, "deepIntTriple")(
      (IntBench.deepIntTriple _).tupled
    )

    // ListBench — saturated → 1 unreached → 2-3 unreached → 4+ unreached.
    _ <- benchAll(lists, "isEmpty")(ListBench.isEmpty)
    _ <- benchAll(lists, "sizeClass")(ListBench.sizeClass)
    _ <- benchAll(lists, "allSameSign")(ListBench.allSameSign)
    _ <- benchAll[(List[Int], List[Int])](lists, "lengthCompare")(
      (ListBench.lengthCompare _).tupled
    )
    _ <- benchAll(lists, "sumClass")(ListBench.sumClass)
    _ <- benchAll(lists, "isStrictlySorted")(ListBench.isStrictlySorted)
    _ <- benchAll(lists, "allEqual")(ListBench.allEqual)
    _ <- benchAll(lists, "extremesGap")(ListBench.extremesGap)
    _ <- benchAll(lists, "palindromeClass")(ListBench.palindromeClass)
    _ <- benchAll(lists, "listMaxMin")(ListBench.listMaxMin)
    _ <- benchAll[(List[Int], List[Int])](lists, "prefixCheck")(
      (ListBench.prefixCheck _).tupled
    )
    _ <- benchAll[(List[Int], List[Int])](lists, "isReverseOf")(
      (ListBench.isReverseOf _).tupled
    )
    _ <- benchAll[(List[Int], List[Int])](lists, "haveSameMultiset")(
      (ListBench.haveSameMultiset _).tupled
    )
    _ <- benchAll(lists, "allPrime")(ListBench.allPrime)
    _ <- benchAll(lists, "primeListShape")(ListBench.primeListShape)
    _ <- benchAll(lists, "isPermutation")(ListBench.isPermutation)
    _ <- benchAll[(List[Int], List[Int])](lists, "deepListRelation")(
      (ListBench.deepListRelation _).tupled
    )
    _ <- benchAll(lists, "deepListShape")(ListBench.deepListShape)
  } yield ()
}
