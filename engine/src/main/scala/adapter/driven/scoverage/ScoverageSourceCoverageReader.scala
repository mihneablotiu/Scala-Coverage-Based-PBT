package adapter.driven.scoverage

import cats.effect.IO
import domain.{BranchCounter, MethodSourceCoverage, Pos}
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._

/** Reads scoverage's per-statement coverage data for a single SUT.
  *
  *   - The *compile-time* statement map is at
  *     `<sutRoot>/target/scala-2.13/scoverage-data/scoverage.coverage`. Each `Statement` carries a
  *     `branch: Boolean` flag set by the scoverage compiler plugin — this is the source-level
  *     authority on "what's a branch in this method?", covering `if`/`else`, `match`, case guards,
  *     `try`/`catch`, `for` filters, `&&`/`||` short-circuits, etc., uniformly.
  *   - The *runtime* invocation log is in `scoverage.measurements.*` files in the same directory.
  *
  * `cleanStaleData` is idempotent within one JVM: scoverage's runtime caches a `FileWriter` per
  * data-dir on first invocation, so we may only delete the stale measurement files once, before any
  * instrumented SUT code runs.
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir = "target/scala-2.13/scoverage-data"

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir = sutRoot.resolve(DataSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile
    private val cleanedOnce = new AtomicBoolean(false)

    override def cleanStaleData: IO[Unit] = IO {
      if (cleanedOnce.compareAndSet(false, true) && Files.isDirectory(dataDir)) {
        Files
          .list(dataDir)
          .iterator()
          .asScala
          .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
          .foreach(Files.deleteIfExists(_))
      }
    }

    override def methodCoverage(
        sourceFile: Path,
        methodName: String
    ): IO[MethodSourceCoverage] = IO {
      if (!coverageFile.exists()) MethodSourceCoverage.Empty
      else {
        val coverage = Serializer.deserialize(coverageFile, sutRoot.toFile)
        val measurementFiles = IOUtils.findMeasurementFiles(dataDir.toFile)
        coverage.apply(IOUtils.invoked(measurementFiles.toSeq))

        val sourceFileName = sourceFile.getFileName.toString
        val statements = coverage.statements.iterator
          .filter(s => s.source.endsWith(sourceFileName) && s.location.method == methodName)
          .toVector

        val branchStmts = statements.filter(_.branch)
        MethodSourceCoverage(
          branchCounter = BranchCounter(
            covered = branchStmts.count(_.count > 0),
            total = branchStmts.size
          ),
          branchPositions = branchStmts.iterator.map(s => Pos(s.start)).toSet,
          coveredPositions = statements.iterator.filter(_.count > 0).map(s => Pos(s.start)).toSet
        )
      }
    }
  }
}
