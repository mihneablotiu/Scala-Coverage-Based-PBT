package adapter.driving.fileSystem

import cats.effect.IO
import domain.Strategy
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.Path

/** Filesystem-bound [[TestRunner]]: SUT source and output base directory are construction-time constants, so the port itself stays free of filesystem
  * details. Each (method, strategy) pair lives in its own folder: `outBase/<sourceStem>/<methodName>/<strategy.name>/`.
  */
final class FileSystemTestRunner(
    handler: TestRunnerHandler,
    sourceFile: Path,
    outBase: Path
) extends TestRunner {

  override def runTests[A](
      methodName: String,
      strategy: Strategy[A]
  )(property: A => Boolean): IO[Unit] = {
    val stem = sourceFile.getFileName.toString.stripSuffix(".scala")
    val out  = outBase.resolve(stem).resolve(methodName).resolve(strategy.name)
    handler.handle(sourceFile, out, methodName, strategy)(property)
  }
}
