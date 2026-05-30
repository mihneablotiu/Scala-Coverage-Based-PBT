package adapter.driven.scoverage

import domain.Pos
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.Using

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

    // `Files.list` returns a directory-handle-holding `Stream`; close it deterministically with `Using`.
    if (Files.isDirectory(dataDir))
      Using.resource(Files.list(dataDir)) { stream =>
        stream
          .iterator()
          .asScala
          .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
          .foreach(Files.deleteIfExists)
      }

    private lazy val staticCoverage: scoverage.domain.Coverage = {
      if (!coverageFile.exists())
        sys.error(
          s"scoverage data file not found at $coverageFile. " +
            "Make sure `coverageEnabled := true` is set on the SUT and `sut/compile` ran."
        )
      Serializer.deserialize(coverageFile, sutRoot.toFile)
    }

    // Bucket the full statement list (~751 entries across the SUT) by enclosing method name once,
    // so each per-iteration `coverage(...)` call only walks the handful of statements for one method
    // instead of re-scanning every statement in the codebase. Source-file disambiguation is still
    // done per-call via `endsWith` to keep the original matching semantics.
    private lazy val statementsByMethod: Map[String, List[scoverage.domain.Statement]] =
      staticCoverage.statements.toList.groupBy(_.location.method)

    override def coverage(sourceFile: Path, methodName: String): Set[Pos] = {
      val sourceFileName = sourceFile.getFileName.toString
      val firedIds       = readFiredIds
      statementsByMethod
        .getOrElse(methodName, Nil)
        .iterator
        .filter(s => s.source.endsWith(sourceFileName))
        .filter(s => firedIds(s.id))
        .map(_.start)
        .toSet
    }

    private def readFiredIds: Set[Int] =
      IOUtils.invoked(IOUtils.findMeasurementFiles(dataDir.toFile).toSeq).iterator.map(_._1).toSet
  }
}
