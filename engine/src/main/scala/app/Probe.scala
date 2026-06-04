package app

import benchmark._
import pbt.{Pbt, Report}
import pbt.analysis.BranchTree
import pbt.gen.Generatable
import pbt.strategy.Strategy

import java.nio.file.{Path, Paths}

/** Scratch runner for exploring how the tactics fare on individual (complex/real) methods at a high input budget — WITHOUT writing the statistics
  * reports, so the K=30 sweep's metrics survive. Prints covered/total per method; one forked JVM per strategy (scoverage's `Invoker` is process-global,
  * but distinct methods each fire fresh within a run, so one pass is fine).
  *
  * Usage: `engine/runMain app.Probe <strategy> <seed> <inputs>` (defaults: random, 1, 1000000).
  */
object Probe {

  private val SutRoot                   = Paths.get("sut")
  private def source(cat: String): Path = Paths.get(s"sut/src/main/scala/benchmark/$cat.scala")

  def main(args: Array[String]): Unit = {
    val strategy = args.headOption.flatMap(Strategy.byName).getOrElse(Strategy.all.head)
    val seed     = args.lift(1).flatMap(_.toLongOption).getOrElse(1L)
    val inputs   = args.lift(2).flatMap(_.toIntOption).getOrElse(1000000)
    val runner   = new Pbt(SutRoot)

    def show[A](cat: String, method: String, r: Report[A]): Unit = {
      val leaves  = r.tree.fold(List.empty[BranchTree.Leaf])(BranchTree.leaves)
      val covered = leaves.count(l => r.feedback.covered(l.pos))
      val pct     = if (leaves.isEmpty) 0.0 else 100.0 * covered / leaves.size
      println(f"  $cat%-10s $method%-15s $covered%2d/${leaves.size}%-2d = $pct%5.1f%%  (${r.elapsedMillis}ms)")
    }
    def run[A: Generatable](cat: String, method: String)(body: A => Any): Unit =
      show(cat, method, runner.check[A](source(cat), method, strategy, seed, inputs)(in => { body(in); true }))
    def run2[A: Generatable, B: Generatable](cat: String, method: String)(body: (A, B) => Any): Unit =
      show(cat, method, runner.check[(A, B)](source(cat), method, strategy, seed, inputs)(in => { body.tupled(in); true }))
    def run3[A: Generatable, B: Generatable, C: Generatable](cat: String, method: String)(body: (A, B, C) => Any): Unit =
      show(cat, method, runner.check[(A, B, C)](source(cat), method, strategy, seed, inputs)(in => { body.tupled(in); true }))

    println(s"# strategy=${strategy.name} seed=$seed inputs=$inputs")
    run[String]("RealWorld", "atoi")(RealWorld.atoi)
    run[String]("RealWorld", "isValidNumber")(RealWorld.isValidNumber)
    run[String]("RealWorld", "romanToInt")(RealWorld.romanToInt)
    run2[String, String]("RealWorld", "compareVersion")(RealWorld.compareVersion)
    run[String]("RealWorld", "isValidIPv4")(RealWorld.isValidIPv4)
    run3[Int, Int, Int]("RealWorld", "isValidDate")(RealWorld.isValidDate)
    run[String]("RealWorld", "luhn")(RealWorld.luhn)
    run2[String, Int]("Mixed", "triage")(Mixed.triage)
    run3[String, Int, Int]("Mixed", "dispatch")(Mixed.dispatch)
  }
}
