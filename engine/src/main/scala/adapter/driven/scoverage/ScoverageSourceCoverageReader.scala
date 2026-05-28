package adapter.driven.scoverage

import cats.effect.IO
import domain.MethodSourceCoverage
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** scoverage-backed source-level coverage reader.
  *
  * The constructor wipes stale measurement files once, atomically — required because scoverage's
  * `Invoker` caches `FileWriter`s after the first SUT execution, so deleting later would orphan
  * them. Doing it at construction means callers don't have to remember to call any setup method,
  * and the port stays a one-method interface.
  *
  * The static statement map is deserialised on first read and cached in a `lazy val`; the
  * measurement files are small append-only logs and re-read per call.
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir = "target/scala-2.13/scoverage-data"

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir = sutRoot.resolve(DataSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile

    // One-shot wipe at construction. The composition root creates exactly one reader per JVM, so
    // this runs once and any subsequent SUT execution sees a clean measurements directory.
    if (Files.isDirectory(dataDir))
      Files
        .list(dataDir)
        .iterator()
        .asScala
        .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
        .foreach(Files.deleteIfExists)

    /** Missing `scoverage.coverage` means the SUT was never compiled with `coverageEnabled := true`
      * (or the data dir was wiped between compile and run). Failing loudly here surfaces the
      * misconfiguration; silently returning empty coverage would have every benchmark report
      * `0 of 0 branches` and look like genuine results.
      */
    private lazy val staticCoverage: scoverage.domain.Coverage = {
      if (!coverageFile.exists())
        sys.error(
          s"scoverage data file not found at $coverageFile. The SUT was not compiled with " +
            "scoverage instrumentation — make sure `coverageEnabled := true` is set on the SUT " +
            "project and that `sut/compile` ran before this engine."
        )
      Serializer.deserialize(coverageFile, sutRoot.toFile)
    }

    override def coverage(sourceFile: Path, methodName: String): IO[MethodSourceCoverage] = IO {
      val sourceFileName = sourceFile.getFileName.toString
      val firedIds = readFiredIds
      val methodStmts = staticCoverage.statements.iterator
        .filter(s => s.source.endsWith(sourceFileName) && s.location.method == methodName)
        .toVector
      MethodSourceCoverage(
        coveredPositions = methodStmts.iterator.filter(s => firedIds(s.id)).map(_.start).toSet,
        branchLines = methodStmts.iterator.filter(_.branch).map(s => s.start -> s.line).toMap
      )
    }

    private def readFiredIds: Set[Int] =
      IOUtils.invoked(IOUtils.findMeasurementFiles(dataDir.toFile).toSeq).iterator.map(_._1).toSet
  }
}
