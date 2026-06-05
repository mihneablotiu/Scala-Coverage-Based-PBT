package pbt

import scoverage.serialize.Serializer

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._
import scala.util.Using

/** Reads scoverage's statement definitions and the statement ids fired so far. Stale measurements are wiped on construction so a run starts clean.
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

  def methodStatements(sourceFile: Path, method: String): List[Coverage.StatementTarget] = {
    methodStatementData(sourceFile, method)
      .sortBy(s => (s.start, s.id))
      .map { s =>
        Coverage.StatementTarget(s.id, s.branch)
      }
  }

  def firedTargetIds(sourceFile: Path, targets: List[Coverage.StatementTarget]): Set[Int] = {
    val fired = firedStatementIds(sourceFile)
    targets.map(_.id).filter(fired).toSet
  }

  private def firedStatementIds(sourceFile: Path): Set[Int] = {
    val fired          = measurementFiles.flatMap(Files.readAllLines(_).asScala).flatMap(_.trim.takeWhile(!_.isWhitespace).toIntOption).toSet
    val fileStatements = statements.getOrElse(sourceFile.getFileName.toString, Nil).map(_.id).toSet
    fired.intersect(fileStatements)
  }

  private def measurementFiles: List[Path] =
    if (!Files.isDirectory(dataDir)) Nil
    else Using.resource(Files.list(dataDir))(_.iterator().asScala.filter(_.getFileName.toString.startsWith("scoverage.measurements.")).toList)

  private def methodStatementData(sourceFile: Path, method: String): List[scoverage.domain.Statement] =
    statements
      .getOrElse(sourceFile.getFileName.toString, Nil)
      .filter(s => s.location.method == method && !s.ignored)

  private def fileName(source: String): String = source.replace('\\', '/').split('/').last
}

object Coverage {
  final case class StatementTarget(id: Int, branch: Boolean)
}
