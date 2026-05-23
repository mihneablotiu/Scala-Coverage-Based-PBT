package app

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.jacoco.JacocoBranchCoverageTracker
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

/** Composition root. Wires driven adapters, builds one handler, instantiates one driving
  * adapter per SUT source file, and dispatches each benchmark.
  */
object Main extends IOApp.Simple {

  private val ClassesDir: Path  = Paths.get("sut/target/scala-2.13/classes")
  private val SutRoot: Path     = Paths.get("sut")
  private val ReportsBase: Path = Paths.get("engine/reports")
  private val BoolSrc: Path     = Paths.get("sut/src/main/scala/benchmark/bool/BoolBench.scala")
  private val IntSrc: Path      = Paths.get("sut/src/main/scala/benchmark/int/IntBench.scala")
  private val ListSrc: Path     = Paths.get("sut/src/main/scala/benchmark/list/ListBench.scala")

  private val testParams: Test.Parameters =
    Test.Parameters.default.withInitialSeed(rng.Seed(0L))

  private val sourceCoverage = ScoverageSourceCoverageReader(SutRoot)

  private val handler: TestRunnerHandler = new TestRunnerHandler(
    tracker = JacocoBranchCoverageTracker(ClassesDir),
    treeBuilder = ScalametaBranchTreeBuilder(),
    sourceCoverage = sourceCoverage,
    writer = FileSystemCoverageReportWriter(),
    params = testParams
  )

  private val bools: TestRunner = new FileSystemTestRunner(handler, BoolSrc, ReportsBase)
  private val ints:  TestRunner = new FileSystemTestRunner(handler, IntSrc,  ReportsBase)
  private val lists: TestRunner = new FileSystemTestRunner(handler, ListSrc, ReportsBase)

  /** Run a benchmark: call the SUT method on each generated input, ignore the return value. */
  private def bench[A: Arbitrary](runner: TestRunner, name: String)(body: A => Any): IO[Unit] =
    runner.runTests[A](name, Strategy.Random)(a => { body(a); true })

  override val run: IO[Unit] = for {
    _ <- sourceCoverage.cleanStaleData

    _ <- bench(bools, "identity")(BoolBench.identity)
    _ <- bench(bools, "negate")(BoolBench.negate)

    _ <- bench(ints, "isPositive")(IntBench.isPositive)
    _ <- bench(ints, "parity")(IntBench.parity)
    _ <- bench(ints, "sign")(IntBench.sign)
    _ <- bench(ints, "mod97is13")(IntBench.mod97is13)
    _ <- bench(ints, "divisibleByThousand")(IntBench.divisibleByThousand)
    _ <- bench(ints, "inSmallRange")(IntBench.inSmallRange)
    _ <- bench(ints, "isPowerOfTwo")(IntBench.isPowerOfTwo)
    _ <- bench(ints, "isMagic")(IntBench.isMagic)
    _ <- bench(ints, "category")(IntBench.category)
    _ <- bench(ints, "classify")(IntBench.classify)

    _ <- bench(lists, "isEmpty")(ListBench.isEmpty)
    _ <- bench(lists, "containsZero")(ListBench.containsZero)
    _ <- bench(lists, "sizeClass")(ListBench.sizeClass)
    _ <- bench(lists, "firstIsLast")(ListBench.firstIsLast)
    _ <- bench(lists, "allPositive")(ListBench.allPositive)
    _ <- bench(lists, "isSorted")(ListBench.isSorted)
    _ <- bench(lists, "allEqual")(ListBench.allEqual)
    _ <- bench(lists, "isPalindrome")(ListBench.isPalindrome)
    _ <- bench(lists, "sumIsSeven")(ListBench.sumIsSeven)
    _ <- bench(lists, "consecutiveTriple")(ListBench.consecutiveTriple)
  } yield ()
}
