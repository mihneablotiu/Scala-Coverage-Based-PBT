package usecase

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import domain.{
  BranchCounter,
  BranchSummary,
  BranchTree,
  CoverageMeasurement,
  InputRecord,
  MethodSourceCoverage,
  MethodTree,
  SessionFeedback,
  SessionReport,
  Strategy
}
import org.scalacheck.{Arbitrary, Gen, Prop, Test}
import port.driven.{
  BranchCoverageTracker,
  BranchTreeBuilder,
  CoverageReportWriter,
  SourceCoverageReader
}

import java.nio.file.Path

/** Use case for one fuzz session.
  *
  * Drives **ScalaCheck's `Test.check`** against the SUT method — random strategy plugs the implicit
  * `Arbitrary[A].arbitrary` into `Prop.forAll`, guided strategy plugs a stateful `Gen[A]` that
  * reads from a shared session accumulator. Either way, every iteration's input is the same one
  * ScalaCheck would produce inside `Prop.forAll`'s loop; the only thing the engine adds is a
  * coverage-tracking step inside the property body.
  *
  * Constructed with its four driven ports + the `Test.Parameters` (run config: iteration count,
  * size schedule, seed). Knows nothing about *which* file or output path is being targeted — those
  * are the driving adapter's job, passed in per-call.
  */
final class TestRunnerHandler(
    tracker: BranchCoverageTracker,
    treeBuilder: BranchTreeBuilder,
    sourceCoverage: SourceCoverageReader,
    writer: CoverageReportWriter,
    params: Test.Parameters
) {

  def handle[A: Arbitrary](
      sourceFile: Path,
      outDir: Path,
      methodName: String,
      strategy: Strategy
  )(property: A => Boolean): IO[Unit] = for {
    _ <- sourceCoverage.cleanStaleData
    _ <- tracker.reset
    acc <- runScalaCheck(sourceFile, methodName, strategy, property)
    tree <- treeBuilder.build(sourceFile, methodName)
    src <- sourceCoverage.methodCoverage(sourceFile, methodName)
    _ <- warnOnDrift(methodName, tree, src)
    _ <- writer.write(buildReport(sourceFile, methodName, acc, tree, src), outDir)
  } yield ()

  /** Run ScalaCheck's `Test.check` for `params.minSuccessfulTests` iterations. The property body
    * runs the SUT, measures coverage via JaCoCo, and records each input in `acc`. Wrapped in
    * `IO.blocking` because `Test.check` is synchronous.
    */
  private def runScalaCheck[A](
      sourceFile: Path,
      methodName: String,
      strategy: Strategy,
      property: A => Boolean
  )(implicit arb: Arbitrary[A]): IO[SessionAccumulator[A]] = IO.blocking {
    val acc = new SessionAccumulator[A]
    val sourceFileName = sourceFile.getFileName.toString

    val gen: Gen[A] = strategy match {
      case Strategy.Random => arb.arbitrary
      case Strategy.Guided => guidedGen(acc, arb.arbitrary)
    }

    val prop = Prop.forAll(gen) { input =>
      try property(input)
      catch { case _: Throwable => () }
      val m = tracker.measure(sourceFileName, methodName).unsafeRunSync()
      acc.observe(input, m)
      true
    }
    Test.check(params, prop)
    acc
  }

  /** Placeholder coverage-guided generator. Wrapped in `Gen.delay` so it's re-evaluated on every
    * ScalaCheck iteration, giving it a chance to inspect `acc.snapshot` (the cumulative feedback so
    * far) before deciding what to emit. Today it just prints the feedback and delegates to the
    * random `Gen` — real selection / mutation logic plugs in here.
    */
  private def guidedGen[A](acc: SessionAccumulator[A], random: Gen[A]): Gen[A] = Gen.delay {
    val f = acc.snapshot
    val covered = f.cumulativeCoverage.values.iterator.map(_.covered).sum
    val total = f.cumulativeCoverage.values.iterator.map(_.total).sum
    val lastInput = f.history.lastOption.fold("—")(_.input.toString)
    println(
      f"[guided] iter=${f.iteration}%-3d  coverage=$covered/$total  lines=${f.cumulativeCoverage.size}%-2d  lastInput=$lastInput"
    )
    random
  }

  private def warnOnDrift(
      methodName: String,
      tree: Option[MethodTree],
      src: MethodSourceCoverage
  ): IO[Unit] = {
    val astArms = tree.fold(0)(t => BranchTree.armCount(t.body))
    val scov = src.branchCounter.total
    if (astArms == 0 || scov == 0 || astArms == scov) IO.unit
    else
      IO.println(
        s"[warn] $methodName: source-level branch drift — scoverage reports $scov branch arm(s), " +
          s"BranchTree models $astArms. Picture is missing decision points; add the construct " +
          s"to ScalametaBranchTreeBuilder.visit."
      )
  }

  private def buildReport[A](
      sourceFile: Path,
      methodName: String,
      acc: SessionAccumulator[A],
      tree: Option[MethodTree],
      src: MethodSourceCoverage
  ): SessionReport[A] = {
    val f = acc.snapshot
    val finalCovered = f.growthCurve.lastOption.getOrElse(0)
    val saturation =
      if (f.growthCurve.isEmpty) None else Some(f.growthCurve.indexOf(finalCovered))
    val branches = f.cumulativeCoverage.iterator.map { case (line, c) =>
      line -> BranchSummary(
        c,
        f.hitCountsByLine.getOrElse(line, 0),
        f.firstHitsByLine.get(line)
      )
    }.toMap
    SessionReport(
      methodName = methodName,
      sourceFile = sourceFile,
      totalInputs = f.iteration,
      methodTree = tree,
      sourceBranchCounter = src.branchCounter,
      branchesByLine = branches,
      inputLog = f.history,
      growthCurve = f.growthCurve,
      saturationInputIndex = saturation,
      coveredPositions = src.coveredPositions
    )
  }
}

/** Mutable accumulator: the property body calls `observe` each iteration; `snapshot` produces an
  * immutable [[SessionFeedback]] for downstream consumers (report builder, guided strategy).
  */
private final class SessionAccumulator[A] {
  private val history = scala.collection.mutable.ArrayBuffer.empty[InputRecord[A]]
  private var cumulative: Map[Int, BranchCounter] = Map.empty
  private val hitCounts = scala.collection.mutable.Map.empty[Int, Int]
  private val firstHits = scala.collection.mutable.Map.empty[Int, Int]
  private val growth = scala.collection.mutable.ArrayBuffer.empty[Int]

  def observe(input: A, m: CoverageMeasurement): Unit = {
    val exercised = m.perInput.iterator.collect { case (l, c) if c.covered > 0 => l }.toSet
    val idx = history.length
    history += InputRecord(idx, input, exercised)
    cumulative = m.cumulative
    exercised.foreach { l =>
      hitCounts.update(l, hitCounts.getOrElse(l, 0) + 1)
      if (!firstHits.contains(l)) firstHits.update(l, idx)
    }
    growth += m.cumulative.values.iterator.map(_.covered).sum
  }

  def snapshot: SessionFeedback[A] = SessionFeedback(
    history = history.toVector,
    cumulativeCoverage = cumulative,
    hitCountsByLine = hitCounts.toMap,
    firstHitsByLine = firstHits.toMap,
    growthCurve = growth.toVector
  )
}
