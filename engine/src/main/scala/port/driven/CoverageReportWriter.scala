package port.driven

import cats.effect.IO
import domain.SessionReport

import java.nio.file.Path

/** Writes a finished [[SessionReport]] to some persistent surface (filesystem, network, …).
  * Adapters decide the formats. Parameterised over the input type `A` of the report.
  */
trait CoverageReportWriter {
  def write[A](report: SessionReport[A], outDir: Path): IO[Unit]
}
