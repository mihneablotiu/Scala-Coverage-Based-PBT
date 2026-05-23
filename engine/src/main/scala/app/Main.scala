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
    _ <- bench(bools, "identity", Strategy.Random)(BoolBench.identity)
    _ <- bench(bools, "negate", Strategy.Random)(BoolBench.negate)

    _ <- bench(ints, "isPositive", Strategy.Random)(IntBench.isPositive)
    _ <- bench(ints, "parity", Strategy.Random)(IntBench.parity)
    _ <- bench(ints, "sign", Strategy.Random)(IntBench.sign)
    _ <- bench(ints, "mod97is13", Strategy.Random)(IntBench.mod97is13)
    _ <- bench(ints, "divisibleByThousand", Strategy.Random)(IntBench.divisibleByThousand)
    _ <- bench(ints, "inSmallRange", Strategy.Random)(IntBench.inSmallRange)
    _ <- bench(ints, "isPowerOfTwo", Strategy.Random)(IntBench.isPowerOfTwo)
    _ <- bench(ints, "isMagic", Strategy.Random)(IntBench.isMagic)
    _ <- bench(ints, "category", Strategy.Random)(IntBench.category)
    _ <- bench(ints, "classify", Strategy.Random)(IntBench.classify)

    _ <- bench(lists, "isEmpty", Strategy.Random)(ListBench.isEmpty)
    _ <- bench(lists, "containsZero", Strategy.Random)(ListBench.containsZero)
    _ <- bench(lists, "sizeClass", Strategy.Random)(ListBench.sizeClass)
    _ <- bench(lists, "firstIsLast", Strategy.Random)(ListBench.firstIsLast)
    _ <- bench(lists, "allPositive", Strategy.Random)(ListBench.allPositive)
    _ <- bench(lists, "isSorted", Strategy.Random)(ListBench.isSorted)
    _ <- bench(lists, "allEqual", Strategy.Random)(ListBench.allEqual)
    _ <- bench(lists, "isPalindrome", Strategy.Random)(ListBench.isPalindrome)
    _ <- bench(lists, "sumIsSeven", Strategy.Random)(ListBench.sumIsSeven)
    _ <- bench(lists, "consecutiveTriple", Strategy.Random)(ListBench.consecutiveTriple)
  } yield ()
}
