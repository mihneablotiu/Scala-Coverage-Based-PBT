package adapter.driving.fileSystem

import adapter.driven.fileSystem.FileSystemCoverageReportWriter
import adapter.driven.gen.{GuidedInputGenerator, RandomInputGenerator}
import adapter.driven.jacoco.JacocoBranchCoverageTracker
import adapter.driven.scalameta.ScalametaBranchTreeBuilder
import adapter.driven.scoverage.ScoverageSourceCoverageReader
import domain.Strategy
import port.driven.InputGenerator
import port.driving.TestRunner
import usecase.TestRunnerHandler

import java.nio.file.{Path, Paths}

/** Driving adapter: composes the JaCoCo + Scalameta + scoverage + file-system stack into a
  * [[TestRunner]] and resolves a [[Strategy]] into a concrete [[InputGenerator]]. Concrete
  * decisions about *where* SUT bytecode and scoverage data live stay here, out of the use case.
  */
object FileSystemTestRunner {

  /** sbt convention: compiled `.class` files of the SUT subproject. */
  private val ClassesDir: Path = Paths.get("sut", "target", "scala-2.13", "classes")

  /** Root of the SUT subproject — scoverage's data dir is relative to this. */
  private val SutRoot: Path = Paths.get("sut")

  private val InitialSeed: Long = 0L

  def apply(): TestRunner =
    TestRunnerHandler(
      tracker = JacocoBranchCoverageTracker(ClassesDir),
      treeBuilder = ScalametaBranchTreeBuilder(),
      sourceCoverage = ScoverageSourceCoverageReader(SutRoot),
      writer = FileSystemCoverageReportWriter(),
      generators = generatorFor
    )

  private def generatorFor(strategy: Strategy): InputGenerator = strategy match {
    case Strategy.Random => RandomInputGenerator(InitialSeed)
    case Strategy.Guided => GuidedInputGenerator(RandomInputGenerator(InitialSeed))
  }
}
