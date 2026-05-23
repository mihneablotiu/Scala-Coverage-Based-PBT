package adapter.driven.scoverage

import cats.effect.IO
import domain.{MethodSourceCoverage, Pos}
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** scoverage-backed source-level coverage reader.
  *
  * `methodCoverage` reads scoverage's runtime state live. Cheap: the static map is cached in a
  * `lazy val`, the measurement files are small append-only logs.
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir = "target/scala-2.13/scoverage-data"

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir = sutRoot.resolve(DataSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile

    /** Deserialised once on first read — the static statement map doesn't change during a JVM run.
      */
    private lazy val staticCoverage: Option[scoverage.domain.Coverage] =
      Option.when(coverageFile.exists())(Serializer.deserialize(coverageFile, sutRoot.toFile))

    /** One-shot wipe of stale measurement files. The `lazy val` gives thread-safe atomic
      * single-execution semantics, so the use case can call `cleanStaleData` at the start of every
      * session and only the first call actually deletes anything. Required because scoverage's
      * `Invoker` caches `FileWriter`s after the first SUT execution — deleting later would orphan
      * them.
      */
    private lazy val cleanedOnce: Unit =
      if (Files.isDirectory(dataDir))
        Files
          .list(dataDir)
          .iterator()
          .asScala
          .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
          .foreach(Files.deleteIfExists)

    override def cleanStaleData: IO[Unit] = IO(cleanedOnce)

    override def methodCoverage(
        sourceFile: Path,
        methodName: String
    ): IO[MethodSourceCoverage] = IO {
      staticCoverage.fold(MethodSourceCoverage.Empty) { coverage =>
        val sourceFileName = sourceFile.getFileName.toString
        val firedIds = readFiredIds
        val methodStmts = coverage.statements.iterator
          .filter(s => s.source.endsWith(sourceFileName) && s.location.method == methodName)
          .toVector
        MethodSourceCoverage(
          coveredPositions =
            methodStmts.iterator.filter(s => firedIds(s.id)).map(s => Pos(s.start)).toSet,
          branchLines =
            methodStmts.iterator.filter(_.branch).map(s => Pos(s.start) -> s.line).toMap
        )
      }
    }

    private def readFiredIds: Set[Int] =
      IOUtils.invoked(IOUtils.findMeasurementFiles(dataDir.toFile).toSeq).iterator.map(_._1).toSet
  }
}
