package pbt

import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.Using

/** Reads which source offsets scoverage has fired so far. scoverage writes the coverage definition once and appends a fired statement id to a
  * measurement file each time one runs; we just read those files whole on every call (they are tiny). Stale measurements are wiped on construction so
  * a run starts clean.
  */
final class Coverage(sutRoot: Path) {

  private val dataDir      = sutRoot.resolve("target/scala-2.13/scoverage-data")
  private val coverageFile = dataDir.resolve("scoverage.coverage").toFile

  measurementFiles.foreach(Files.deleteIfExists)

  // id → statement, grouped by source file name; read once.
  private lazy val statements: Map[String, List[scoverage.domain.Statement]] = {
    if (!coverageFile.exists())
      sys.error(s"scoverage data not found at $coverageFile — set `coverageEnabled := true` on the SUT and run `sut/compile`.")
    Serializer.deserialize(coverageFile, sutRoot.toFile).statements.toList.groupBy(s => fileName(s.source))
  }

  /** The source offsets of every statement fired so far in `sourceFile`. */
  def firedOffsets(sourceFile: Path): Set[Int] = {
    val fired = measurementFiles.flatMap(Files.readAllLines(_).asScala).flatMap(_.trim.takeWhile(!_.isWhitespace).toIntOption).toSet
    statements.getOrElse(sourceFile.getFileName.toString, Nil).filter(s => fired(s.id)).map(_.start).toSet
  }

  private def measurementFiles: List[Path] =
    if (!Files.isDirectory(dataDir)) Nil
    else Using.resource(Files.list(dataDir))(_.iterator().asScala.filter(_.getFileName.toString.startsWith("scoverage.measurements.")).toList)

  private def fileName(source: String): String = source.replace('\\', '/').split('/').last
}
