package adapter.driven.jacoco

import cats.effect.IO
import domain.{BranchCounter, CoverageMeasurement}
import org.jacoco.agent.rt.RT
import org.jacoco.core.analysis.{Analyzer, CoverageBuilder}
import org.jacoco.core.data.{ExecutionDataReader, ExecutionDataStore, SessionInfoStore}
import port.driven.BranchCoverageTracker

import java.io.ByteArrayInputStream
import java.nio.file.Path
import scala.jdk.CollectionConverters._

/** JaCoCo-backed branch coverage tracker.
  *
  *   - Runtime probe state lives in the JaCoCo agent (`RT.getAgent`), dumped+reset between inputs
  *     and merged into an in-memory cumulative store. Probe arrays OR-merge, which is what gives
  *     correct cumulative coverage even when different inputs take different branches at the same
  *     line.
  *   - Probe → source projection is done by JaCoCo's `Analyzer`, which walks the original bytecode
  *     and yields a per-method, per-line `BranchCounter` (covered/total).
  *
  * Per-direction attribution (`then` vs `else`) is *not* in JaCoCo's stable public API — that's why
  * downstream consumers see only line- level counters, and per-AST-node colouring is done via
  * scoverage instead.
  */
object JacocoBranchCoverageTracker {

  def apply(classesDir: Path): BranchCoverageTracker = new Live(classesDir)

  private final class Live(classesDir: Path) extends BranchCoverageTracker {

    private lazy val agent = RT.getAgent
    private val cumulativeStore = new ExecutionDataStore

    override def reset: IO[Unit] = IO {
      agent.reset()
      cumulativeStore.reset()
    }

    override def measure(sourceFileName: String, methodName: String): IO[CoverageMeasurement] = IO {
      val perInputStore = readDelta()
      perInputStore.getContents.forEach(d => cumulativeStore.put(d))
      CoverageMeasurement(
        perInput = lineCountersOf(perInputStore, sourceFileName, methodName),
        cumulative = lineCountersOf(cumulativeStore, sourceFileName, methodName)
      )
    }

    /** Pull the agent's latest probe data, then reset it. */
    private def readDelta(): ExecutionDataStore = {
      val store = new ExecutionDataStore
      val reader = new ExecutionDataReader(new ByteArrayInputStream(agent.getExecutionData(true)))
      reader.setExecutionDataVisitor(store)
      reader.setSessionInfoVisitor(new SessionInfoStore)
      reader.read()
      store
    }

    /** Project an `ExecutionDataStore` through `Analyzer` and pick out the branch counter for each
      * line of the requested source/method.
      */
    private def lineCountersOf(
        store: ExecutionDataStore,
        sourceFileName: String,
        methodName: String
    ): Map[Int, BranchCounter] = {
      val builder = new CoverageBuilder
      new Analyzer(store, builder).analyzeAll(classesDir.toFile)
      builder.getClasses.asScala.iterator
        .filter(_.getSourceFileName == sourceFileName)
        .flatMap(_.getMethods.asScala.iterator.filter(_.getName == methodName))
        .flatMap { m =>
          (m.getFirstLine to m.getLastLine).iterator.flatMap { ln =>
            val line = m.getLine(ln)
            val total = line.getBranchCounter.getTotalCount
            Option.when(total > 0)(
              ln -> BranchCounter(line.getBranchCounter.getCoveredCount, total)
            )
          }
        }
        .toMap
    }
  }
}
