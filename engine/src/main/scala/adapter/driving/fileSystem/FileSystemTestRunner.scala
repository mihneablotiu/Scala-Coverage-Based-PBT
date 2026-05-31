package adapter.driving.fileSystem

import domain.{Mutator, Pooled}
import org.scalacheck.Arbitrary
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.Path

/** Filesystem-bound [[TestRunner]]. Each cell lands at `outBase/<sourceStem>/<methodName>/<strategyName>[/<cellSuffix>]/`. The optional `cellSuffix`
  * (e.g. `seed=NN`) is the Makefile multi-seed sweep's last path segment.
  */
final class FileSystemTestRunner(
    handler: TestRunnerHandler,
    sourceFile: Path,
    outBase: Path,
    cellSuffix: Option[String] = None
) extends TestRunner {

  override def runTests[A: Arbitrary: Mutator: Pooled](
      methodName: String,
      strategyName: String
  )(property: A => Boolean): Unit = {
    val stem = sourceFile.getFileName.toString.stripSuffix(".scala")
    val base = outBase.resolve(stem).resolve(methodName).resolve(strategyName)
    val out  = cellSuffix.fold(base)(base.resolve)
    handler.handle[A](sourceFile, out, methodName, strategyName)(property)
  }
}
