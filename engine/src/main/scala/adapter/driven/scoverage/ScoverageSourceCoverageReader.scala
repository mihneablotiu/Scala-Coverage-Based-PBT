package adapter.driven.scoverage

import cats.effect.IO
import domain.Pos
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._

/** Reads scoverage's per-statement coverage data for a single SUT.
  *
  *   - The *compile-time* statement map is at
  *     `<sutRoot>/target/scala-2.13/scoverage-data/scoverage.coverage`.
  *   - The *runtime* invocation log is in `scoverage.measurements.*` files in the same directory.
  *
  * For a given (source file, method), we return the start offsets of every scoverage statement
  * invoked at runtime *and* declared in that method.
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

    override def coveredPositions(sourceFile: Path, methodName: String): IO[Set[Pos]] = IO {
      if (!coverageFile.exists()) Set.empty[Pos]
      else {
        val coverage = Serializer.deserialize(coverageFile, sutRoot.toFile)
        val measurementFiles = IOUtils.findMeasurementFiles(dataDir.toFile)
        coverage.apply(IOUtils.invoked(measurementFiles.toSeq))

        val sourceFileName = sourceFile.getFileName.toString
        coverage.statements.iterator
          .filter(s =>
            s.count > 0 && s.source.endsWith(sourceFileName) && s.location.method == methodName
          )
          .map(s => Pos(s.start))
          .toSet
      }
    }
  }
}
