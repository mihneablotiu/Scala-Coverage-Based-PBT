package adapter.driving.fileSystem

import domain.Strategy
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.Path

/** Filesystem-bound [[TestRunner]]: SUT source and output base directory are construction-time constants, so the port itself stays free of filesystem
  * details. Each (method, strategy[, cellSuffix]) tuple lives in its own folder: `outBase/<sourceStem>/<methodName>/<strategy.name>[/<cellSuffix>]/`.
  *
  * `cellSuffix` is the optional last path segment used by the Makefile-driven multi-seed sweep to land each run under `seed=NN/`; leaving it `None`
  * preserves the pre-multi-seed layout.
  */
final class FileSystemTestRunner(
    handler: TestRunnerHandler,
    sourceFile: Path,
    outBase: Path,
    cellSuffix: Option[String] = None
) extends TestRunner {

  override def runTests[A](
      methodName: String,
      strategy: Strategy[A]
  )(property: A => Boolean): Unit = {
    val stem = sourceFile.getFileName.toString.stripSuffix(".scala")
    val base = outBase.resolve(stem).resolve(methodName).resolve(strategy.name)
    val out  = cellSuffix.fold(base)(base.resolve)
    handler.handle(sourceFile, out, methodName, strategy)(property)
  }
}
