package pbt

import pbt.analysis.SourceSpan
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

  def methodStatements(sourceFile: Path, span: SourceSpan): List[Coverage.StatementTarget] = {
    val source = Files.readString(sourceFile)
    statements
      .getOrElse(sourceFile.getFileName.toString, Nil)
      .filter(s => span.contains(s.start))
      .sortBy(_.start)
      .map { s =>
        val end  = s.end.max(s.start + 1).min(source.length)
        val text = source.slice(s.start, end).replaceAll("\\s+", " ").trim.take(100)
        Coverage.StatementTarget(s.id, s.start, end, lineOf(source, s.start), text)
      }
  }

  def firedStatementIds(sourceFile: Path): Set[Int] = {
    val fired          = measurementFiles.flatMap(Files.readAllLines(_).asScala).flatMap(_.trim.takeWhile(!_.isWhitespace).toIntOption).toSet
    val fileStatements = statements.getOrElse(sourceFile.getFileName.toString, Nil).map(_.id).toSet
    fired.intersect(fileStatements)
  }

  private def measurementFiles: List[Path] =
    if (!Files.isDirectory(dataDir)) Nil
    else Using.resource(Files.list(dataDir))(_.iterator().asScala.filter(_.getFileName.toString.startsWith("scoverage.measurements.")).toList)

  private def fileName(source: String): String         = source.replace('\\', '/').split('/').last
  private def lineOf(source: String, offset: Int): Int = source.take(offset).count(_ == '\n') + 1
}

object Coverage {
  final case class StatementTarget(id: Int, start: Int, end: Int, line: Int, text: String)
}
