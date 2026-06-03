package pbt

import scoverage.serialize.Serializer

import java.io.RandomAccessFile
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Using

/** Reads, after each input, the source offsets of every statement scoverage has fired so far in a given file.
  *
  * scoverage writes each fired statement id once, append-only, so we *tail* the measurement files (read only newly appended bytes and union the ids)
  * rather than re-parsing them every iteration — O(new ids) per call. The static id → position map is deserialised once. Construction wipes stale
  * measurement files (doing it later would orphan scoverage's cached writers).
  */
final class Coverage(sutRoot: Path) {

  private val dataDir      = sutRoot.resolve("target/scala-2.13/scoverage-data")
  private val coverageFile = dataDir.resolve("scoverage.coverage").toFile
  private val fired        = mutable.Set.empty[Int]
  private val offsets      = mutable.Map.empty[Path, Long]
  private var files        = List.empty[Path]

  measurementFiles().foreach(Files.deleteIfExists)

  private lazy val statementsBySource: Map[String, List[scoverage.domain.Statement]] = {
    if (!coverageFile.exists())
      sys.error(s"scoverage data not found at $coverageFile — set `coverageEnabled := true` on the SUT and run `sut/compile`.")
    Serializer.deserialize(coverageFile, sutRoot.toFile).statements.toList.groupBy(s => baseName(s.source))
  }

  /** Source offsets of every statement fired so far in `sourceFile`. */
  def firedOffsets(sourceFile: Path): Set[Pos] = {
    if (files.isEmpty) files = measurementFiles()
    files.foreach(tail)
    statementsBySource.getOrElse(sourceFile.getFileName.toString, Nil).iterator.filter(s => fired(s.id)).map(_.start).toSet
  }

  private def measurementFiles(): List[Path] =
    if (!Files.isDirectory(dataDir)) Nil
    else Using.resource(Files.list(dataDir))(_.iterator().asScala.filter(_.getFileName.toString.startsWith("scoverage.measurements.")).toList)

  private def tail(file: Path): Unit = {
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

  private def baseName(source: String): String = source.replace('\\', '/').split('/').last
}
