package adapter.driven.scoverage

import domain.Pos
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** scoverage-backed reader.
  *
  * The constructor wipes stale `scoverage.measurements.*` files once. Doing it later would orphan the `FileWriter`s scoverage's `Invoker` caches on
  * first SUT execution. The static statement map is deserialised lazily and cached.
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir = "target/scala-2.13/scoverage-data"

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir      = sutRoot.resolve(DataSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile

    if (Files.isDirectory(dataDir))
      Files
        .list(dataDir)
        .iterator()
        .asScala
        .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
        .foreach(Files.deleteIfExists)

    private lazy val staticCoverage: scoverage.domain.Coverage = {
      if (!coverageFile.exists())
        sys.error(
          s"scoverage data file not found at $coverageFile. " +
            "Make sure `coverageEnabled := true` is set on the SUT and `sut/compile` ran."
        )
      Serializer.deserialize(coverageFile, sutRoot.toFile)
    }

    override def coverage(sourceFile: Path, methodName: String): Set[Pos] = {
      val sourceFileName = sourceFile.getFileName.toString
      val firedIds       = readFiredIds
      staticCoverage.statements.iterator
        .filter(s => s.source.endsWith(sourceFileName) && s.location.method == methodName)
        .filter(s => firedIds(s.id))
        .map(_.start)
        .toSet
    }

    private def readFiredIds: Set[Int] =
      IOUtils.invoked(IOUtils.findMeasurementFiles(dataDir.toFile).toSeq).iterator.map(_._1).toSet
  }
}
