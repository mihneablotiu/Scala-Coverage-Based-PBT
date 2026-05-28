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
  * Knows **nothing about the driven side** — scoverage, Scalameta, the writer. Its only
  * collaborator is the injected [[TestRunnerHandler]], which it forwards to with the filesystem
  * context plugged in. Composition (constructing the handler with its driven adapters) happens in
  * the app's composition root.
  */
final class FileSystemTestRunner(
    handler: TestRunnerHandler,
    sourceFile: Path,
    outBase: Path
) extends TestRunner {

  /** Output goes to `<outBase> / <sourceFileStem> / <methodName> / <strategy.name>` so each
    * (method, strategy) pair lives in its own folder — e.g.
    * `engine/reports/IntBench/isPrime/random/` and
    * `engine/reports/IntBench/isPrime/mutation-guided/` sit side-by-side under the same method
    * directory.
    */
  override def runTests[A: Arbitrary](
      methodName: String,
      strategy: Strategy
  )(exercise: A => Any): IO[Unit] = {
    val stem = sourceFile.getFileName.toString.stripSuffix(".scala")
    val out = outBase.resolve(stem).resolve(methodName).resolve(strategy.name)
    handler.handle(sourceFile, out, methodName, strategy)(exercise)
  }
}
