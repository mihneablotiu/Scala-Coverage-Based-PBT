package adapter.driven.scoverage

import domain.Pos
import port.driven.SourceCoverageReader
import scoverage.serialize.Serializer

import java.io.RandomAccessFile
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Using

/** scoverage-backed reader. scoverage writes each fired statement id once, append-only, so coverage is read by tailing the measurement files
  * ([[MeasurementTail]]) rather than re-parsing them each iteration. The static id → position map is deserialised and cached once. The constructor
  * wipes stale measurement files (doing it later would orphan scoverage's cached writers).
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir = "target/scala-2.13/scoverage-data"

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir      = sutRoot.resolve(DataSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile
    private val tail         = new MeasurementTail(dataDir)

    if (Files.isDirectory(dataDir))
      Using.resource(Files.list(dataDir)) { stream =>
        stream.iterator().asScala.filter(_.getFileName.toString.startsWith("scoverage.measurements.")).foreach(Files.deleteIfExists)
      }

    private lazy val staticCoverage: scoverage.domain.Coverage = {
      if (!coverageFile.exists())
        sys.error(s"scoverage data not found at $coverageFile. Set `coverageEnabled := true` on the SUT and run `sut/compile`.")
      Serializer.deserialize(coverageFile, sutRoot.toFile)
    }

    // Bucket statements by source-file basename once; each call then inspects only that file's statements.
    private lazy val statementsBySource: Map[String, List[scoverage.domain.Statement]] =
      staticCoverage.statements.toList.groupBy(s => fileName(s.source))

    override def coverage(sourceFile: Path): Set[Pos] = {
      val fired = tail.firedIds
      statementsBySource
        .getOrElse(sourceFile.getFileName.toString, Nil)
        .iterator
        .filter(s => fired(s.id))
        .map(_.start)
        .toSet
    }

    private def fileName(source: String): String = source.replace('\\', '/').split('/').last
  }
}

/** Tails scoverage's append-only measurement files, reading only bytes appended since the last call and unioning new ids into a cumulative set —
  * O(new ids) per call. Each id is written once, so the set is exactly the statements fired so far.
  */
private[scoverage] final class MeasurementTail(dataDir: Path) {

  private val fired             = mutable.Set.empty[Int]
  private val offsets           = mutable.Map.empty[Path, Long]
  private var files: List[Path] = Nil

  def firedIds: collection.Set[Int] = {
    if (files.isEmpty) files = discover()
    files.foreach(readNew)
    fired
  }

  private def discover(): List[Path] =
    if (!Files.isDirectory(dataDir)) Nil
    else
      Using.resource(Files.list(dataDir)) { stream =>
        stream.iterator().asScala.filter(_.getFileName.toString.startsWith("scoverage.measurements.")).toList
      }

  private def readNew(file: Path): Unit = {
    val from = offsets.getOrElse(file, 0L)
    if (Files.size(file) > from)
      Using.resource(new RandomAccessFile(file.toFile, "r")) { raf =>
        raf.seek(from)
        var line = raf.readLine()
        while (line != null) {
          line.trim.takeWhile(!_.isWhitespace).toIntOption.foreach(fired += _)
          line = raf.readLine()
        }
        offsets(file) = raf.getFilePointer
      }
  }
}
