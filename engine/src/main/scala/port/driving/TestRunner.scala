package port.driving

import cats.effect.IO
import domain.Strategy

import java.nio.file.Path

trait TestRunner {
  def run(
      sourceFile: Path,
      methodName: String,
      property: Int => Boolean,
      strategy: Strategy,
      outDir: Path
  ): IO[Unit]
}
