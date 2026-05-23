package adapter.driven.scoverage

import domain.{BranchCounter, MethodSourceCoverage, Pos}
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

    /** Deserialised once on first read — the static statement map doesn't change during a JVM run. */
    private lazy val staticCoverage: Option[scoverage.domain.Coverage] =
      Option.when(coverageFile.exists())(Serializer.deserialize(coverageFile, sutRoot.toFile))

    /** One-shot side effect: wipes stale measurement files on first invocation. `lazy val` gives
      * us thread-safe atomic single-execution — `cleanStaleData` can be called from every
      * `handle()` and only the first call actually wipes. Required because scoverage's `Invoker`
      * caches `FileWriter`s after the first SUT execution, so deleting later orphans them.
      */
    private lazy val cleanedOnce: Unit =
      if (Files.isDirectory(dataDir))
        Files.list(dataDir).iterator().asScala
          .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
          .foreach(Files.deleteIfExists)

    override def cleanStaleData(): Unit = cleanedOnce

    override def methodCoverage(sourceFile: Path, methodName: String): MethodSourceCoverage =
      staticCoverage.fold(MethodSourceCoverage.Empty) { coverage =>
        val sourceFileName = sourceFile.getFileName.toString
        val firedIds = readFiredIds
        val methodStmts = coverage.statements.iterator
          .filter(s => s.source.endsWith(sourceFileName) && s.location.method == methodName)
          .toVector
        val branchStmts = methodStmts.filter(_.branch)
        MethodSourceCoverage(
          branchCounter = BranchCounter(
            covered = branchStmts.count(s => firedIds(s.id)),
            total = branchStmts.size
          ),
          coveredPositions =
            methodStmts.iterator.filter(s => firedIds(s.id)).map(s => Pos(s.start)).toSet
        )
      }

    private def readFiredIds: Set[Int] =
      IOUtils.invoked(IOUtils.findMeasurementFiles(dataDir.toFile).toSeq).iterator.map(_._1).toSet
  }
}
