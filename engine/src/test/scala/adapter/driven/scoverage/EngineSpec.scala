package adapter.driven.scoverage

import domain.{Generatable, SessionFeedback, Strategy}
import org.scalacheck.{Arbitrary, Gen, Prop, Test, rng}

import java.nio.file.{Files, StandardOpenOption}

/** Pins the two correctness claims of the refactor: that `random` is faithful to stock ScalaCheck, and that the incremental measurement reader sees
  * the same ids as a full read.
  */
class EngineSpec extends munit.FunSuite {

  test("random strategy draws the same inputs as a plain ScalaCheck forAll") {
    val n      = 500
    val params = Test.Parameters.default.withMinSuccessfulTests(n).withInitialSeed(rng.Seed(42L)).withWorkers(1)

    // The engine's random loop: strategy.gen wrapped in Gen.delay, feedback grown as a side effect (ignored by random).
    val engineInputs = Vector.newBuilder[Int]
    locally {
      var fb  = SessionFeedback.empty[Int]
      val gen = Gen.delay(Strategy.Random(Generatable[Int]).gen(fb))
      Test.check(
        params,
        Prop.forAllNoShrink(gen) { (i: Int) =>
          engineInputs += i
          fb = fb.append(i, Set.empty)
          true
        }
      )
    }

    // What a ScalaCheck user writes without the engine.
    val stockInputs = Vector.newBuilder[Int]
    Test.check(
      params,
      Prop.forAllNoShrink(Arbitrary.arbInt.arbitrary) { (i: Int) =>
        stockInputs += i
        true
      }
    )

    assertEquals(engineInputs.result(), stockInputs.result())
  }

  test("MeasurementTail unions only newly-appended ids, matching a full read") {
    val dir  = Files.createTempDirectory("scoverage-meas")
    val file = dir.resolve("scoverage.measurements.test.0")
    val tail = new MeasurementTail(dir)

    assertEquals(tail.firedIds.toSet, Set.empty[Int]) // measurement file not created yet

    Files.write(file, "4\n3\n2\n1\n".getBytes)
    assertEquals(tail.firedIds.toSet, Set(1, 2, 3, 4))

    Files.write(file, "1\n5\n6\n".getBytes, StandardOpenOption.APPEND) // 1 repeats, 5/6 are new
    assertEquals(tail.firedIds.toSet, Set(1, 2, 3, 4, 5, 6))
  }
}
