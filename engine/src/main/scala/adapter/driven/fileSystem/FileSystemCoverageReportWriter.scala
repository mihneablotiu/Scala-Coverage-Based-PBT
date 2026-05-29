package adapter.driven.fileSystem

import cats.effect.IO
import domain.{BranchTree, Pos, SessionReport}
import port.driven.CoverageReportWriter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Writes a [[SessionReport]] to `outDir/coverage.json`.
  *
  * One file per (method, strategy) cell. Everything visual — per-cell DOT branch trees, per-cell growth charts, cross-strategy comparison artefacts —
  * is produced downstream by the Python scripts under `engine/reports/scripts/`.
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
  *     "branchTree":   <nested tree with firstHitInput annotated on each leaf>
  *   }
  * }}}
  */
object FileSystemCoverageReportWriter {

  def apply(): CoverageReportWriter = new Live

  private final class Live extends CoverageReportWriter {
    override def write[A](report: SessionReport[A], outDir: Path): IO[Unit] = IO {
      Files.createDirectories(outDir)
      Files.writeString(
        outDir.resolve("coverage.json"),
        renderJson(report),
        StandardCharsets.UTF_8
      )
    }.void
  }

  private def renderJson[A](r: SessionReport[A]): String = {
    val firstHits: Map[Pos, Int] = r.feedback.history.iterator
      .flatMap(rec => rec.newlyCoveredBranches.iterator.map(_ -> rec.index))
      .toMap
    val treeJson = r.branchTree.fold("null")(t => renderTree(t, firstHits, indent = 4))
    s"""{
       |  "method": ${jstr(r.methodName)},
       |  "sourceFile": ${jstr(r.sourceFile.getFileName.toString)},
       |  "strategy": ${jstr(r.strategy)},
       |  "totalInputs": ${r.feedback.iteration},
       |  "growthCurve": ${r.feedback.growthCurve.mkString("[", ", ", "]")},
       |  "branchTree": $treeJson
       |}
       |""".stripMargin
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
