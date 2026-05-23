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
import org.scalacheck.{rng, Test}
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Composition root.
  *
  * Wires the driven adapters, builds one [[TestRunnerHandler]], instantiates one driving adapter
  * ([[FileSystemTestRunner]]) per SUT source file, then dispatches each benchmark. Every benchmark
  * block reads like a normal ScalaCheck `forAll` body — same shape, same arbitraries.
  */
object Main extends IOApp.Simple {

  // ── Filesystem constants ────────────────────────────────────────────────
  private val ClassesDir: Path = Paths.get("sut/target/scala-2.13/classes")
  private val SutRoot: Path = Paths.get("sut")
  private val ReportsBase: Path = Paths.get("engine/reports")
  private val BoolSrc: Path = Paths.get("sut/src/main/scala/benchmark/bool/BoolBench.scala")
  private val IntSrc: Path = Paths.get("sut/src/main/scala/benchmark/int/IntBench.scala")
  private val ListSrc: Path = Paths.get("sut/src/main/scala/benchmark/list/ListBench.scala")

  // ── Run configuration (ScalaCheck's Test.Parameters, fixed seed for reproducibility) ──
  private val testParams: Test.Parameters =
    Test.Parameters.default.withInitialSeed(rng.Seed(0L))

  // ── Driven adapters built once, injected ───────────────────────────────
  private val sourceCoverage = ScoverageSourceCoverageReader(SutRoot)

  private val handler: TestRunnerHandler = new TestRunnerHandler(
    tracker = JacocoBranchCoverageTracker(ClassesDir),
    treeBuilder = ScalametaBranchTreeBuilder(),
    sourceCoverage = sourceCoverage,
    writer = FileSystemCoverageReportWriter(),
    params = testParams
  )

  // ── Driving adapters: one per source file ──────────────────────────────
  private val bools: TestRunner = new FileSystemTestRunner(handler, BoolSrc, ReportsBase)
  private val ints: TestRunner = new FileSystemTestRunner(handler, IntSrc, ReportsBase)
  private val lists: TestRunner = new FileSystemTestRunner(handler, ListSrc, ReportsBase)

  // ── Benchmarks ─────────────────────────────────────────────────────────
  // `cleanStaleData` runs once at the very start (scoverage's runtime caches `FileWriter`s
  // after the first SUT execution — wiping has to happen before that). Each block below is
  // the property body — same shape as a normal ScalaCheck `forAll`.
  override val run: IO[Unit] = for {
    _ <- sourceCoverage.cleanStaleData

    _ <- bools.runTests("identity", Strategy.Random) { (b: Boolean) =>
      BoolBench.identity(b); true
    }
    _ <- bools.runTests("negate", Strategy.Random) { (b: Boolean) =>
      BoolBench.negate(b); true
    }

    _ <- ints.runTests("isPositive", Strategy.Random) { (n: Int) =>
      IntBench.isPositive(n); true
    }
    _ <- ints.runTests("parity", Strategy.Random) { (n: Int) =>
      IntBench.parity(n); true
    }
    _ <- ints.runTests("sign", Strategy.Random) { (n: Int) =>
      IntBench.sign(n); true
    }
    _ <- ints.runTests("mod97is13", Strategy.Random) { (n: Int) =>
      IntBench.mod97is13(n); true
    }
    _ <- ints.runTests("divisibleByThousand", Strategy.Random) { (n: Int) =>
      IntBench.divisibleByThousand(n); true
    }
    _ <- ints.runTests("inSmallRange", Strategy.Random) { (n: Int) =>
      IntBench.inSmallRange(n); true
    }
    _ <- ints.runTests("isPowerOfTwo", Strategy.Random) { (n: Int) =>
      IntBench.isPowerOfTwo(n); true
    }
    _ <- ints.runTests("isMagic", Strategy.Random) { (n: Int) =>
      IntBench.isMagic(n); true
    }
    _ <- ints.runTests("category", Strategy.Random) { (n: Int) =>
      IntBench.category(n); true
    }
    _ <- ints.runTests("classify", Strategy.Random) { (n: Int) =>
      IntBench.classify(n); true
    }

    _ <- lists.runTests("isEmpty", Strategy.Random) { (l: List[Int]) =>
      ListBench.isEmpty(l); true
    }
    _ <- lists.runTests("containsZero", Strategy.Random) { (l: List[Int]) =>
      ListBench.containsZero(l); true
    }
    _ <- lists.runTests("sizeClass", Strategy.Random) { (l: List[Int]) =>
      ListBench.sizeClass(l); true
    }
    _ <- lists.runTests("firstIsLast", Strategy.Random) { (l: List[Int]) =>
      ListBench.firstIsLast(l); true
    }
    _ <- lists.runTests("allPositive", Strategy.Random) { (l: List[Int]) =>
      ListBench.allPositive(l); true
    }
    _ <- lists.runTests("isSorted", Strategy.Random) { (l: List[Int]) =>
      ListBench.isSorted(l); true
    }
    _ <- lists.runTests("allEqual", Strategy.Random) { (l: List[Int]) =>
      ListBench.allEqual(l); true
    }
    _ <- lists.runTests("isPalindrome", Strategy.Random) { (l: List[Int]) =>
      ListBench.isPalindrome(l); true
    }
    _ <- lists.runTests("sumIsSeven", Strategy.Random) { (l: List[Int]) =>
      ListBench.sumIsSeven(l); true
    }
    _ <- lists.runTests("consecutiveTriple", Strategy.Random) { (l: List[Int]) =>
      ListBench.consecutiveTriple(l); true
    }
  } yield ()
}
