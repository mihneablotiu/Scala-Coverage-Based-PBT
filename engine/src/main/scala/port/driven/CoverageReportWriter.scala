package port.driven

import domain.SessionReport

import java.nio.file.Path

/** Persists a finished [[SessionReport]] somewhere. Adapters decide format and destination. */
trait CoverageReportWriter {
  def write[A](report: SessionReport[A], outDir: Path): Unit
}
