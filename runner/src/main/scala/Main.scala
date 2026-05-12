import adapter.driving.fileSystem.FileSystemTestRunner
import branchhard.HardToReachBranches
import cats.effect.{IO, IOApp}
import domain.Strategy
import port.driving.TestRunner

import java.nio.file.{Path, Paths}

object Main extends IOApp.Simple {

  private val SourceFile: Path =
    Paths.get("sut", "src", "main", "scala", "branchhard", "HardToReachBranches.scala")
  private val OutBase: Path = Paths.get("runner", "out")

  override val run: IO[Unit] = {
    val testRunner = FileSystemTestRunner()
    for {
      _ <- test(testRunner, "classify97", exercise(HardToReachBranches.classify97), Strategy.Random)
      _ <- test(testRunner, "isPositive", exercise(HardToReachBranches.isPositive), Strategy.Random)
      _ <- test(testRunner, "category", exercise(HardToReachBranches.category), Strategy.Random)
    } yield ()
  }

  private def exercise[A](sut: Int => A): Int => Boolean = n => { sut(n); true }

  private def test(
      runner: TestRunner,
      methodName: String,
      property: Int => Boolean,
      strategy: Strategy
  ): IO[Unit] =
    runner.run(SourceFile, methodName, property, strategy, OutBase.resolve(methodName))
}
