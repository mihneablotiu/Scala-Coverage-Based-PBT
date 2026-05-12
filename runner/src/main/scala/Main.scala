import adapter.driving.fileSystem.FileSystemTestRunner
import benchmark.bool.BoolBench
import benchmark.int.IntBench
import benchmark.list.ListBench
import cats.effect.{IO, IOApp}
import domain.Strategy
import org.scalacheck.Gen
import port.driving.TestRunner

import java.nio.file.{Path, Paths}

/** Benchmark suite entry point.
  *
  * Each entry below registers one SUT method together with the ScalaCheck generator that drives it.
  * `Strategy.Random` is the baseline; the future guided strategy will be run from the same registry
  * to produce a head-to-head comparison.
  */
object Main extends IOApp.Simple {

  private val OutBase: Path = Paths.get("runner", "out")

  // ── Source paths (one per data-type file) ─────────────────────────
  private val BoolSrc: Path =
    Paths.get("sut", "src", "main", "scala", "benchmark", "bool", "BoolBench.scala")
  private val IntSrc: Path =
    Paths.get("sut", "src", "main", "scala", "benchmark", "int", "IntBench.scala")
  private val ListSrc: Path =
    Paths.get("sut", "src", "main", "scala", "benchmark", "list", "ListBench.scala")

  // ── Generators ────────────────────────────────────────────────────
  private val boolGen: Gen[Boolean] = Gen.oneOf(true, false)
  private val intGen: Gen[Int] = Gen.chooseNum(Int.MinValue, Int.MaxValue)

  // List of Ints, length 0..20 — wide enough to vary structure, narrow enough to
  // keep `inputs.csv` readable.
  private val listGen: Gen[List[Int]] =
    Gen.choose(0, 20).flatMap(n => Gen.listOfN(n, intGen))

  // ── Registry ──────────────────────────────────────────────────────
  private val benches: List[BenchRun] = List(
    // Boolean — 2 methods (sanity)
    BenchRun(BoolSrc, "identity", (b: Boolean) => { BoolBench.identity(b); true }, boolGen),
    BenchRun(BoolSrc, "negate", (b: Boolean) => { BoolBench.negate(b); true }, boolGen),

    // Int — 10 methods (3 easy + 7 hard)
    BenchRun(IntSrc, "isPositive", (n: Int) => { IntBench.isPositive(n); true }, intGen),
    BenchRun(IntSrc, "parity", (n: Int) => { IntBench.parity(n); true }, intGen),
    BenchRun(IntSrc, "sign", (n: Int) => { IntBench.sign(n); true }, intGen),
    BenchRun(IntSrc, "classify97", (n: Int) => { IntBench.classify97(n); true }, intGen),
    BenchRun(IntSrc, "isMagic", (n: Int) => { IntBench.isMagic(n); true }, intGen),
    BenchRun(IntSrc, "inSmallRange", (n: Int) => { IntBench.inSmallRange(n); true }, intGen),
    BenchRun(
      IntSrc,
      "divisibleByThousand",
      (n: Int) => { IntBench.divisibleByThousand(n); true },
      intGen
    ),
    BenchRun(IntSrc, "powerOfTwo", (n: Int) => { IntBench.powerOfTwo(n); true }, intGen),
    BenchRun(IntSrc, "luckyPositive", (n: Int) => { IntBench.luckyPositive(n); true }, intGen),
    BenchRun(IntSrc, "category", (n: Int) => { IntBench.category(n); true }, intGen),

    // List[Int] — 10 methods (3 easy + 7 hard)
    BenchRun(ListSrc, "emptiness", (l: List[Int]) => { ListBench.emptiness(l); true }, listGen),
    BenchRun(ListSrc, "hasPositive", (l: List[Int]) => { ListBench.hasPositive(l); true }, listGen),
    BenchRun(
      ListSrc,
      "lengthParity",
      (l: List[Int]) => { ListBench.lengthParity(l); true },
      listGen
    ),
    BenchRun(ListSrc, "isSorted", (l: List[Int]) => { ListBench.isSorted(l); true }, listGen),
    BenchRun(ListSrc, "isDistinct", (l: List[Int]) => { ListBench.isDistinct(l); true }, listGen),
    BenchRun(
      ListSrc,
      "isPalindrome",
      (l: List[Int]) => { ListBench.isPalindrome(l); true },
      listGen
    ),
    BenchRun(
      ListSrc,
      "containsAnswer",
      (l: List[Int]) => { ListBench.containsAnswer(l); true },
      listGen
    ),
    BenchRun(ListSrc, "allPositive", (l: List[Int]) => { ListBench.allPositive(l); true }, listGen),
    BenchRun(ListSrc, "firstIsZero", (l: List[Int]) => { ListBench.firstIsZero(l); true }, listGen),
    BenchRun(
      ListSrc,
      "consecutiveTriple",
      (l: List[Int]) => { ListBench.consecutiveTriple(l); true },
      listGen
    )
  )

  override val run: IO[Unit] = {
    val testRunner = FileSystemTestRunner()
    benches.foldLeft(IO.unit)((acc, b) =>
      acc.flatMap(_ => b.run(testRunner, Strategy.Random, OutBase))
    )
  }
}

/** One benchmark entry. Hides the input type `A` behind a uniform `run` method so the registry can
  * be a plain `List[BenchRun]` rather than `List[Bench[?]]` with existentials at the call site.
  */
sealed trait BenchRun {
  def run(runner: TestRunner, strategy: Strategy, outBase: Path): IO[Unit]
}

object BenchRun {
  def apply[A](
      sourceFile: Path,
      methodName: String,
      property: A => Boolean,
      gen: Gen[A]
  ): BenchRun = new BenchRun {
    override def run(runner: TestRunner, strategy: Strategy, outBase: Path): IO[Unit] =
      runner.run(sourceFile, methodName, property, strategy, gen, outBase.resolve(methodName))
  }
}
