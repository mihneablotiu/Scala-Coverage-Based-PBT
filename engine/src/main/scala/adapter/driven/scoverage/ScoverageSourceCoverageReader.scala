package adapter.driven.scoverage

import cats.effect.IO
import domain.{BranchCounter, MethodSourceCoverage, Pos}
import port.driven.SourceCoverageReader
import scoverage.reporter.IOUtils
import scoverage.serialize.Serializer

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._

/** scoverage-backed source-level coverage reader, organised around **per-method measurement
  * files**.
  *
  * Once `splitMeasurementsByMethod` has run for a method, the framework reads its coverage
  * exclusively from `by-method/<methodName>.measurements` — scoverage's shared `.measurements.*`
  * files are not touched again. That makes `methodCoverage` a thin file parser instead of having
  * to deserialize the full SUT statement map and filter every call.
  *
  * Layout under `<sutRoot>/target/scala-2.13/scoverage-data/`:
  * {{{
  *   scoverage.coverage            — static statement map, compile-time
  *   scoverage.measurements.*      — runtime hit log, scoverage's writers (shared across methods)
  *   by-method/<method>.measurements — our per-method extract, framework-readable
  * }}}
  *
  * Per-method file format (TSV with `#`-prefixed header):
  * {{{
  *   stmt_id<TAB>pos<TAB>line<TAB>is_branch<TAB>count
  * }}}
  * `pos` is the source character offset — enough to rebuild `MethodSourceCoverage` from the
  * file alone, no need to re-consult scoverage's map.
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir     = "target/scala-2.13/scoverage-data"
  private val ByMethodSubdir = "by-method"

  /** One row of the per-method TSV file. */
  private final case class Row(id: Int, pos: Int, line: Int, branch: Boolean, count: Int)

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir      = sutRoot.resolve(DataSubdir)
    private val byMethodDir  = dataDir.resolve(ByMethodSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile
    private val cleanedOnce  = new AtomicBoolean(false)

    /** Idempotent within one JVM. First call wipes both scoverage's runtime measurement files
      * and our per-method files; subsequent calls are no-ops (required because scoverage caches
      * `FileWriter`s after the first SUT execution — deleting later would orphan them).
      */
    override def cleanStaleData: IO[Unit] = for {
      shouldClean <- IO(cleanedOnce.compareAndSet(false, true) && Files.isDirectory(dataDir))
      _           <- IO.whenA(shouldClean)(deleteStaleArtifacts)
    } yield ()

    private def deleteStaleArtifacts: IO[Unit] = IO {
      Files
        .list(dataDir)
        .iterator()
        .asScala
        .filter(_.getFileName.toString.startsWith("scoverage.measurements."))
        .foreach(Files.deleteIfExists)
      Files.createDirectories(byMethodDir)
      Files.list(byMethodDir).iterator().asScala.foreach(Files.deleteIfExists)
    }

    /** Extracts this method's slice of scoverage's runtime data and writes the per-method TSV
      * file at `by-method/<methodName>.measurements`. No-op if the static `scoverage.coverage`
      * map isn't on disk (i.e. SUT never compiled with scoverage enabled).
      */
    override def splitMeasurementsByMethod(
        sourceFile: Path,
        methodName: String
    ): IO[Unit] = for {
      exists <- IO(coverageFile.exists())
      _      <- IO.whenA(exists)(writePerMethodFile(sourceFile, methodName))
    } yield ()

    private def writePerMethodFile(sourceFile: Path, methodName: String): IO[Unit] = IO {
      val coverage = Serializer.deserialize(coverageFile, sutRoot.toFile)
      val measurementFiles = IOUtils.findMeasurementFiles(dataDir.toFile)
      coverage.apply(IOUtils.invoked(measurementFiles.toSeq))

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

    /** Reads the per-method TSV file produced by `splitMeasurementsByMethod` and reconstructs
      * the snapshot. Returns `MethodSourceCoverage.Empty` if the file isn't there (caller forgot
      * to split, or SUT had no scoverage data at all).
      */
    override def methodCoverage(
        sourceFile: Path,
        methodName: String
    ): IO[MethodSourceCoverage] = {
      val file = byMethodDir.resolve(s"$methodName.measurements")
      IO(Files.exists(file)).flatMap {
        case true  => readPerMethodFile(file)
        case false => IO.pure(MethodSourceCoverage.Empty)
      }
    }

    private def readPerMethodFile(file: Path): IO[MethodSourceCoverage] = IO {
      val rows = Files
        .readAllLines(file, StandardCharsets.UTF_8)
        .asScala
        .iterator
        .filter(l => l.nonEmpty && !l.startsWith("#"))
        .map { line =>
          val cols = line.split('\t')
          Row(
            id     = cols(0).toInt,
            pos    = cols(1).toInt,
            line   = cols(2).toInt,
            branch = cols(3).toBoolean,
            count  = cols(4).toInt
          )
        }
        .toList
      val branchRows = rows.filter(_.branch)
      MethodSourceCoverage(
        branchCounter = BranchCounter(
          covered = branchRows.count(_.count > 0),
          total   = branchRows.size
        ),
        coveredPositions = rows.iterator.filter(_.count > 0).map(r => Pos(r.pos)).toSet
      )
    }

  }
}
