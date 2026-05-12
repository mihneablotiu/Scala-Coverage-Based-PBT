package port.driven

import cats.effect.IO
import domain.SessionReport

import java.nio.file.Path

/** Writes a finished [[SessionReport]] to some persistent surface (filesystem, network, …).
  * Adapters decide the formats.
  */
trait CoverageReportWriter {
  def write(report: SessionReport, outDir: Path): IO[Unit]
}
