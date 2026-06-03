package pbt

import pbt.analysis.{BranchTree, Pos}
import pbt.gen.ConstantPool
import pbt.strategy.Feedback

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Everything one run produced. [[write]] serialises it to a single `coverage.json`; all charts and tables are built downstream by the Python
  * scripts. `pool` is the literals mined from the method's branch conditions — exactly the values the pool tactic can inject.
  *
  * {{{
  *   { "method", "sourceFile", "strategy", "totalInputs",
  *     "growthCurve": [cumulative covered leaves after each input],
  *     "pool":        { "ints": [...], "strings": [...] },   // the pool's injectable values
  *     "branchTree":  <nested tree; each leaf carries firstHitInput: int | null> }
  * }}}
  */
final case class Report[A](
    method: String,
    sourceFile: String,
    strategy: String,
    tree: Option[BranchTree],
    pool: ConstantPool,
    feedback: Feedback[A]
) {

  def write(outDir: Path): Unit = {
    Files.createDirectories(outDir)
    Files.writeString(outDir.resolve("coverage.json"), json, StandardCharsets.UTF_8)
  }

  private def json: String = {
    val firstHits = feedback.firstHits
    s"""{
       |  "method": ${str(method)},
       |  "sourceFile": ${str(sourceFile)},
       |  "strategy": ${str(strategy)},
       |  "totalInputs": ${feedback.iteration},
       |  "growthCurve": ${feedback.growthCurve.mkString("[", ", ", "]")},
       |  "pool": ${poolJson(pool)},
       |  "branchTree": ${tree.fold("null")(treeJson(_, firstHits, indent = 4))}
       |}
       |""".stripMargin
  }

  private def poolJson(p: ConstantPool): String = {
    def nums[N](xs: Set[N])(implicit ord: Ordering[N]): String = xs.toSeq.sorted.mkString("[", ", ", "]")
    s"""{"ints": ${nums(p.ints)}, "longs": ${nums(p.longs)}, "doubles": ${nums(p.doubles)}, "strings": ${p.strings.toSeq.sorted.map(str).mkString("[", ", ", "]")}}"""
  }

  private def treeJson(tree: BranchTree, firstHits: Map[Pos, Int], indent: Int): String = {
    val pad      = " " * indent
    val padChild = " " * (indent + 2)
    tree match {
      case BranchTree.Branch(kind, label, arms) =>
        val armsJson = arms.map(a => s"""$padChild{"label": ${str(a.label)}, "body": ${treeJson(a.body, firstHits, indent + 4)}}""").mkString(",\n")
        s"""{
           |$padChild"kind": "branch",
           |$padChild"branchKind": ${str(kind)},
           |$padChild"label": ${str(label)},
           |$padChild"arms": [
           |$armsJson
           |$pad]
           |$pad}""".stripMargin
      case BranchTree.Sequence(children) =>
        val childrenJson = children.map(c => s"$padChild${treeJson(c, firstHits, indent + 2)}").mkString(",\n")
        s"""{
           |$padChild"kind": "sequence",
           |$padChild"children": [
           |$childrenJson
           |$pad]
           |$pad}""".stripMargin
      case l: BranchTree.Leaf =>
        s"""{"kind": "leaf", "line": ${l.line}, "text": ${str(l.text)}, "firstHitInput": ${firstHits.get(l.pos).fold("null")(_.toString)}}"""
    }
  }

  private def str(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
