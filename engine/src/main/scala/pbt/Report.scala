package pbt

import pbt.analysis.BranchTree
import pbt.gen.ConstantPool
import pbt.strategy.Feedback

import java.nio.file.{Files, Path}

/** Everything one run produced, serialised by [[write]] to a single compact `coverage.json`; all charts and tables are built downstream by the Python
  * scripts. The growth curve is not stored — each leaf carries its `firstHitInput`, from which the cumulative curve is reconstructed downstream.
  *
  * {{{
  *   { "method", "sourceFile", "strategy", "totalInputs", "elapsedMillis",
  *     "pool":       { "ints": [...] },
  *     "branchTree": <nested tree; each leaf carries firstHitInput: int | null> }
  * }}}
  */
final case class Report[A](
    method: String,
    sourceFile: String,
    strategy: String,
    tree: Option[BranchTree],
    pool: ConstantPool,
    feedback: Feedback[A],
    elapsedMillis: Long
) {

  def write(outDir: Path): Unit = {
    Files.createDirectories(outDir)
    Files.writeString(outDir.resolve("coverage.json"), json)
  }

  private val firstHits: Map[Int, Int] = feedback.firstHits

  private def json: String = {
    val ints = pool.ints.toSeq.sorted.mkString("[", ",", "]")
    s"""{"method":${quote(method)},"sourceFile":${quote(sourceFile)},"strategy":${quote(strategy)},""" +
      s""""totalInputs":${feedback.iteration},"elapsedMillis":$elapsedMillis,""" +
      s""""pool":{"ints":$ints},""" +
      s""""branchTree":${tree.fold("null")(node)}}"""
  }

  private def node(tree: BranchTree): String = tree match {
    case BranchTree.Branch(kind, label, arms) =>
      val armsJson = arms.map(a => s"""{"label":${quote(a.label)},"body":${node(a.body)}}""").mkString(",")
      s"""{"kind":"branch","branchKind":${quote(kind)},"label":${quote(label)},"arms":[$armsJson]}"""
    case BranchTree.Sequence(children) =>
      s"""{"kind":"sequence","children":[${children.map(node).mkString(",")}]}"""
    case l: BranchTree.Leaf =>
      s"""{"kind":"leaf","line":${l.line},"text":${quote(l.text)},"firstHitInput":${firstHits.get(l.start).fold("null")(_.toString)}}"""
  }

  private def quote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
