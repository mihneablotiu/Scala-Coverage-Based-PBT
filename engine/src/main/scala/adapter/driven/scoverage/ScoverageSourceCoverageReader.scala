package adapter.driven.scoverage

import domain.Pos
import port.driven.SourceCoverageReader
import scoverage.serialize.Serializer

import java.io.RandomAccessFile
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.Using

/** scoverage-backed reader.
  *
  * scoverage's `Invoker` writes each statement id to a measurement file exactly once, on first fire, append-only and flushed immediately. So coverage
  * is read by tailing those files — see [[MeasurementTail]] — rather than re-parsing them whole each iteration. The static statement map (id → source
  * position) is deserialised once and cached.
  *
  * The constructor wipes stale `scoverage.measurements.*` files once; doing it later would orphan the `FileWriter`s scoverage caches on first SUT
  * execution.
  */
object ScoverageSourceCoverageReader {

  private val DataSubdir = "target/scala-2.13/scoverage-data"

  def apply(sutRoot: Path): SourceCoverageReader = new Live(sutRoot)

  private final class Live(sutRoot: Path) extends SourceCoverageReader {

    private val dataDir      = sutRoot.resolve(DataSubdir)
    private val coverageFile = dataDir.resolve("scoverage.coverage").toFile
    private val tail         = new MeasurementTail(dataDir)

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

    // Bucket statements by enclosing method once so each per-iteration call only inspects one method's handful of statements. Source-file
    // disambiguation stays per-call via `endsWith` (two files could share a method name).
    private lazy val statementsByMethod: Map[String, List[scoverage.domain.Statement]] =
      staticCoverage.statements.toList.groupBy(_.location.method)

    override def coverage(sourceFile: Path, methodName: String): Set[Pos] = {
      val sourceFileName = sourceFile.getFileName.toString
      val fired          = tail.firedIds
      statementsByMethod
        .getOrElse(methodName, Nil)
        .iterator
        .filter(s => s.source.endsWith(sourceFileName))
        .filter(s => fired(s.id))
        .map(_.start)
        .toSet
    }
  }
}

/** Incrementally tails scoverage's append-only measurement files. Each call reads only the bytes appended since the previous call (tracked per file)
  * and unions the new ids into a cumulative set — O(new ids) per call instead of re-reading the whole file. Because scoverage writes each id once,
  * the cumulative set is exactly the set of statements fired so far.
  */
private[scoverage] final class MeasurementTail(dataDir: Path) {

  private val fired             = mutable.Set.empty[Int]
  private val offsets           = mutable.Map.empty[Path, Long]
  private var files: List[Path] = Nil

  /** The cumulative set of statement ids fired so far, refreshed from any newly-appended measurement bytes. */
  def firedIds: collection.Set[Int] = {
    // Re-list the directory only until the measurement file appears, then cache it: with one SUT
    // thread (workers=1) scoverage writes a single file, so the per-iteration cost is the read tail,
    // not a directory scan.
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
