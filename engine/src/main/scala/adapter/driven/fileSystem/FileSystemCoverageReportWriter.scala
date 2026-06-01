package adapter.driven.fileSystem

import domain.{BranchTree, ConstantPool, Pos, SessionReport}
import port.driven.CoverageReportWriter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Writes a [[SessionReport]] to `outDir/coverage.json`. One file per (method, strategy, seed) cell. Everything visual is produced downstream by the
  * Python scripts under `engine/reports/scripts/`. The `constantPool` block records which literals the strategy was given (empty for non-pool runs).
  *
  * JSON shape:
  *
  * {{{
  *   {
  *     "method":       "<methodName>",
  *     "sourceFile":   "<file name>",
  *     "strategy":     "<strategy name>",
  *     "totalInputs":  <int>,
  *     "growthCurve":  [<cumulative covered leaves per iteration>],
  *     "constantPool": { "ints": [...], "strings": [...], ... },
  *     "branchTree":   <nested tree with firstHitInput annotated on each leaf>
  *   }
  * }}}
  */
object FileSystemCoverageReportWriter {

  def apply(): CoverageReportWriter = new Live

  private final class Live extends CoverageReportWriter {
    override def write[A](report: SessionReport[A], outDir: Path): Unit = {
      Files.createDirectories(outDir)
      Files.writeString(
        outDir.resolve("coverage.json"),
        renderJson(report),
        StandardCharsets.UTF_8
      )
    }
  }

  private def renderJson[A](r: SessionReport[A]): String = {
    val firstHits: Map[Pos, Int] = r.feedback.history.iterator.zipWithIndex.flatMap { case (delta, i) => delta.iterator.map(_ -> i) }.toMap
    val treeJson                 = r.branchTree.fold("null")(t => renderTree(t, firstHits, indent = 4))
    s"""{
       |  "method": ${jstr(r.methodName)},
       |  "sourceFile": ${jstr(r.sourceName)},
       |  "strategy": ${jstr(r.strategy)},
       |  "totalInputs": ${r.feedback.iteration},
       |  "growthCurve": ${r.feedback.growthCurve.mkString("[", ", ", "]")},
       |  "constantPool": ${renderPool(r.pool)},
       |  "branchTree": $treeJson
       |}
       |""".stripMargin
  }

  /** Sorted per-kind arrays for deterministic diffs across runs. Numeric kinds emit bare JSON numbers; chars / strings emit JSON strings. */
  private def renderPool(p: ConstantPool): String = {
    def nums[T](xs: Set[T])(implicit ord: Ordering[T]): String                      = xs.toSeq.sorted.mkString("[", ", ", "]")
    def strs[T](xs: Set[T], render: T => String)(implicit ord: Ordering[T]): String =
      xs.toSeq.sorted.map(t => jstr(render(t))).mkString("[", ", ", "]")
    s"""{"ints": ${nums(p.ints)}, "longs": ${nums(p.longs)}, "floats": ${nums(p.floats)}, "doubles": ${nums(p.doubles)}, """ +
      s""""booleans": ${nums(p.booleans)}, "chars": ${strs(p.chars, (c: Char) => c.toString)}, "strings": ${strs(p.strings, identity[String])}, """ +
      s""""bytes": ${nums(p.bytes)}, "shorts": ${nums(p.shorts)}}"""
  }

  private def renderTree(tree: BranchTree, firstHits: Map[Pos, Int], indent: Int): String = {
    val pad      = " " * indent
    val padChild = " " * (indent + 2)
    tree match {
      case b: BranchTree.Branch =>
        val arms = b.arms
          .map { a =>
            val body = renderTree(a.body, firstHits, indent + 4)
            s"""$padChild{"label": ${jstr(a.label)}, "body": $body}"""
          }
          .mkString(",\n")
        s"""{
           |$padChild"kind": "branch",
           |$padChild"branchKind": ${jstr(b.kind)},
           |$padChild"label": ${jstr(b.label)},
           |$padChild"arms": [
           |$arms
           |$pad]
           |$pad}""".stripMargin
      case s: BranchTree.Sequence =>
        val children = s.children
          .map(c => s"$padChild${renderTree(c, firstHits, indent + 2)}")
          .mkString(",\n")
        s"""{
           |$padChild"kind": "sequence",
           |$padChild"children": [
           |$children
           |$pad]
           |$pad}""".stripMargin
      case l: BranchTree.Leaf =>
        val firstHit = firstHits.get(l.pos).fold("null")(_.toString)
        s"""{"kind": "leaf", "line": ${l.line}, "text": ${jstr(
            l.text
          )}, "firstHitInput": $firstHit}"""
    }
  }

  private def jstr(s: String): String =
    "\"" +
      s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r") +
      "\""
}
