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
  * Multi-parameter SUT methods are dispatched as `bench[(A, B, ...)]` with `(method _).tupled` to
  * adapt them to the framework's `A => Any` shape. ScalaCheck auto-derives the tuple `Arbitrary`
  * from the per-element `Arbitrary` instances.
  *
  * Benchmark order in each block is **easy → hard**, top to bottom, matching the layout inside
  * each SUT file.
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
    // BoolBench — anchors the framework on the smallest input type.
    _ <- bench(bools, "identity", Strategy.Random)(BoolBench.identity)
    _ <- bench(bools, "negate", Strategy.Random)(BoolBench.negate)
    _ <- bench[(Boolean, Boolean, Boolean)](bools, "threeAgree", Strategy.Random)(
      (BoolBench.threeAgree _).tupled
    )

    // IntBench — value-rarity gradient across one to three integers.
    _ <- bench(ints, "isPositive", Strategy.Random)(IntBench.isPositive)
    _ <- bench(ints, "parity", Strategy.Random)(IntBench.parity)
    _ <- bench(ints, "sign", Strategy.Random)(IntBench.sign)
    _ <- bench(ints, "magnitudeClass", Strategy.Random)(IntBench.magnitudeClass)
    _ <- bench(ints, "mod97", Strategy.Random)(IntBench.mod97)
    _ <- bench(ints, "divisibleByThousand", Strategy.Random)(IntBench.divisibleByThousand)
    _ <- bench[(Int, Int)](ints, "compare", Strategy.Random)((IntBench.compare _).tupled)
    _ <- bench[(Int, Int)](ints, "sumSign", Strategy.Random)((IntBench.sumSign _).tupled)
    _ <- bench[(Int, Int)](ints, "quadrant", Strategy.Random)((IntBench.quadrant _).tupled)
    _ <- bench(ints, "isPerfectSquare", Strategy.Random)(IntBench.isPerfectSquare)
    _ <- bench(ints, "isPerfectCube", Strategy.Random)(IntBench.isPerfectCube)
    _ <- bench(ints, "isPowerOfTwo", Strategy.Random)(IntBench.isPowerOfTwo)
    _ <- bench(ints, "isPalindromeNumber", Strategy.Random)(IntBench.isPalindromeNumber)
    _ <- bench[(Int, Int)](ints, "divisibilityRelation", Strategy.Random)(
      (IntBench.divisibilityRelation _).tupled
    )
    _ <- bench(ints, "classify", Strategy.Random)(IntBench.classify)
    _ <- bench(ints, "magicNumbers", Strategy.Random)(IntBench.magicNumbers)
    _ <- bench[(Int, Int, Int)](ints, "triangleType", Strategy.Random)(
      (IntBench.triangleType _).tupled
    )
    _ <- bench(ints, "deepIntClassify", Strategy.Random)(IntBench.deepIntClassify)
    _ <- bench[(Int, Int)](ints, "deepIntPair", Strategy.Random)(
      (IntBench.deepIntPair _).tupled
    )
    _ <- bench[(Int, Int, Int)](ints, "deepIntTriple", Strategy.Random)(
      (IntBench.deepIntTriple _).tupled
    )

    // ListBench — structural-rarity gradient across one or two lists.
    _ <- bench(lists, "isEmpty", Strategy.Random)(ListBench.isEmpty)
    _ <- bench(lists, "sizeClass", Strategy.Random)(ListBench.sizeClass)
    _ <- bench(lists, "firstClass", Strategy.Random)(ListBench.firstClass)
    _ <- bench(lists, "allSameSign", Strategy.Random)(ListBench.allSameSign)
    _ <- bench(lists, "sumClass", Strategy.Random)(ListBench.sumClass)
    _ <- bench(lists, "headTailRelation", Strategy.Random)(ListBench.headTailRelation)
    _ <- bench(lists, "extremesGap", Strategy.Random)(ListBench.extremesGap)
    _ <- bench(lists, "isSorted", Strategy.Random)(ListBench.isSorted)
    _ <- bench(lists, "isStrictlySorted", Strategy.Random)(ListBench.isStrictlySorted)
    _ <- bench(lists, "allEqual", Strategy.Random)(ListBench.allEqual)
    _ <- bench(lists, "palindromeClass", Strategy.Random)(ListBench.palindromeClass)
    _ <- bench[(List[Int], List[Int])](lists, "lengthCompare", Strategy.Random)(
      (ListBench.lengthCompare _).tupled
    )
    _ <- bench[(List[Int], List[Int])](lists, "isReverseOf", Strategy.Random)(
      (ListBench.isReverseOf _).tupled
    )
    _ <- bench[(List[Int], List[Int])](lists, "haveSameMultiset", Strategy.Random)(
      (ListBench.haveSameMultiset _).tupled
    )
    _ <- bench[(List[Int], Int)](lists, "findTarget", Strategy.Random)(
      (ListBench.findTarget _).tupled
    )
    _ <- bench(lists, "deepListShape", Strategy.Random)(ListBench.deepListShape)
    _ <- bench[(List[Int], List[Int])](lists, "deepListRelation", Strategy.Random)(
      (ListBench.deepListRelation _).tupled
    )
  } yield ()
}
