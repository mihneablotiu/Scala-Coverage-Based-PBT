package adapter.driving.fileSystem

import cats.effect.IO
import domain.Strategy
import org.scalacheck.Arbitrary
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.Path

/** Driving adapter binding the [[TestRunner]] port to a filesystem context. **The SUT source file
  * and the output base directory are construction-time constants of this adapter**, not call-time
  * arguments of the port — that's how the port stays free of filesystem details.
  *
  * Instantiate one per source file (or per `(source, outBase)` pair). Each `run` call resolves the
  * per-method report directory as `outBase / methodName`.
  *
  * Knows **nothing about the driven side** — JaCoCo, scoverage, Scalameta, the writer. Its only
  * collaborator is the injected [[TestRunnerHandler]], which it forwards to with the filesystem
  * context plugged in. Composition (constructing the handler with its driven adapters) happens in
  * the app's composition root.
  */
final class FileSystemTestRunner(
    handler: TestRunnerHandler,
    sourceFile: Path,
    outBase: Path
) extends TestRunner {

  override def runTests[A: Arbitrary](
      methodName: String,
      strategy: Strategy
  )(property: A => Boolean): IO[Unit] =
    handler.handle(sourceFile, outBase.resolve(methodName), methodName, strategy)(property)
}
