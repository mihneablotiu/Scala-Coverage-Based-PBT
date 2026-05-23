package adapter.driven.scoverage

import cats.effect.IO
import domain.{BranchCounter, MethodSourceCoverage, Pos}
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/** scoverage-backed source-level coverage reader.
  *
  *   - `methodCoverage` reads scoverage's runtime state live. Cheap: the static map is cached
  *     in a `lazy val`, the measurement files are small append-only logs.
  *   - `splitMeasurementsByMethod` writes a per-method TSV file under
  *     `by-method/<methodName>.measurements` for user inspection. Side artifact, not in the data
  *     path.
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir = "target/scala-2.13/scoverage-data"
  private val ByMethodSubdir = "by-method"

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir = sutRoot.resolve(DataSubdir)
    private val byMethodDir = dataDir.resolve(ByMethodSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile

    /** Deserialised once on first read — the static statement map doesn't change during a JVM run. */
    private lazy val staticCoverage: Option[scoverage.domain.Coverage] =
      Option.when(coverageFile.exists())(Serializer.deserialize(coverageFile, sutRoot.toFile))

    override def cleanStaleData: IO[Unit] = for {
      exists <- IO(Files.isDirectory(dataDir))
      _ <- IO.whenA(exists)(wipeMeasurements)
    } yield ()

    private def wipeMeasurements: IO[Unit] = IO {
      Files.list(dataDir).iterator().asScala
        .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
        .foreach(Files.deleteIfExists)
      Files.createDirectories(byMethodDir)
      Files.list(byMethodDir).iterator().asScala.foreach(Files.deleteIfExists)
    }

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
    }

    private def readFiredIds: Set[Int] =
      IOUtils.invoked(IOUtils.findMeasurementFiles(dataDir.toFile).toSeq).iterator.map(_._1).toSet

    override def splitMeasurementsByMethod(
        sourceFile: Path,
        methodName: String
    ): IO[Unit] = IO.whenA(coverageFile.exists())(writePerMethodFile(sourceFile, methodName))

    private def writePerMethodFile(sourceFile: Path, methodName: String): IO[Unit] = IO {
      // Fresh deserialisation so `coverage.apply` (which mutates the static map's count fields)
      // doesn't pollute the cached `staticCoverage`.
      val coverage = Serializer.deserialize(coverageFile, sutRoot.toFile)
      coverage.apply(IOUtils.invoked(IOUtils.findMeasurementFiles(dataDir.toFile).toSeq))

      val sourceFileName = sourceFile.getFileName.toString
      val methodStmts = coverage.statements.iterator
        .filter(s => s.source.endsWith(sourceFileName) && s.location.method == methodName)
        .toList
        .sortBy(_.id)

      Files.createDirectories(byMethodDir)
      val outFile = byMethodDir.resolve(s"$methodName.measurements")
      val header =
        s"# scoverage measurements for method '$methodName' in $sourceFileName\n" +
          "# columns: stmt_id\\tpos\\tline\\tis_branch\\tcount\n"
      val rows = methodStmts
        .map(s => s"${s.id}\t${s.start}\t${s.line}\t${s.branch}\t${s.count}")
        .mkString("\n")
      Files.writeString(outFile, header + rows + "\n", StandardCharsets.UTF_8)
    }
  }
}
