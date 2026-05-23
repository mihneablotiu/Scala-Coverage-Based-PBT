package adapter.driven.fileSystem

import cats.effect.IO
import domain.{BranchCounter, BranchSummary, BranchTree, MethodTree, Pos, SessionReport}
import port.driven.CoverageReportWriter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Writes a [[SessionReport]] to disk under `outDir`, organised as:
  *
  * {{{
  *   <outDir>/
  *   ├── summary.txt        — human-readable entry point
  *   ├── visuals/
  *   │   ├── coverage.dot   — Graphviz branch tree, scoverage-coloured
  *   │   └── growth.svg     — chart of branches covered vs. input index
  *   └── data/
  *       ├── coverage.json  — full structured dump
  *       └── inputs.csv     — per-input log (pandas / Excel friendly)
  * }}}
  *
  * Render `coverage.dot` to SVG with `dot -Tsvg visuals/coverage.dot > visuals/coverage.svg`.
  *
  * Generic in the input type `A`; inputs are serialised via `_.toString`, truncated to a fixed
  * width so a single huge value can't blow out the CSV.
  */
object FileSystemCoverageReportWriter {

  /** Max characters of `input.toString` kept in CSV / JSON cells. */
  private val InputDisplayMax = 80

  def apply(): CoverageReportWriter = new Live

  private final class Live extends CoverageReportWriter {

    override def write[A](report: SessionReport[A], outDir: Path): IO[Unit] = {
      val visuals = outDir.resolve("visuals")
      val data = outDir.resolve("data")
      for {
        _ <- IO {
          Files.createDirectories(visuals)
          Files.createDirectories(data)
        }
        _ <- writeFile(outDir, "summary.txt", renderSummary(report))
        _ <- writeFile(visuals, "coverage.dot", renderDot(report))
        _ <- writeFile(visuals, "growth.svg", renderGrowthSvg(report))
        _ <- writeFile(data, "coverage.json", renderJson(report))
        _ <- writeFile(data, "inputs.csv", renderInputsCsv(report))
      } yield ()
    }

    private def writeFile(dir: Path, name: String, content: String): IO[Unit] =
      IO(Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8)).void
  }

  // ────────────────────────────────────────────────────────────────
  // Colour palette
  // ────────────────────────────────────────────────────────────────

  private val Border = "#37474F"
  private val Accent = "#FF6F00"
  private val ChartLine = "#1565C0"
  private val White = "#FFFFFF"
  private val PackageFill = "#ECEFF1"
  private val ClassFill = "#CFD8DC"
  private val NodeCovered = "#A5D6A7"
  private val NodeMissed = "#EF9A9A"
  private val NodeUnknown = "#E1F5FE"
  private val LeafCovered = "#81C784"
  private val LeafMissed = "#E57373"
  private val LeafUnknown = "#ECEFF1"
  private val CovTxt = "#1B5E20"
  private val MisTxt = "#B71C1C"
  private val UnknownTxt = "#666666"

  // ────────────────────────────────────────────────────────────────
  // DOT — Report → Package → Class → Method → BranchTree (recursive)
  // ────────────────────────────────────────────────────────────────

  private def renderDot[A](r: SessionReport[A]): String = {
    val total = r.sourceBranchCounter
    val pkg = r.methodTree.map(_.packageName).filter(_.nonEmpty).getOrElse("<root>")
    val cls = r.methodTree
      .map(_.className)
      .filter(_.nonEmpty)
      .getOrElse(r.sourceFile.getFileName.toString.stripSuffix(".scala"))

    val initial = DotState.empty
      .addNode(boxNode("report", White, reportLabel(r, total), penwidth = 2))
      .addNode(boxNode("pkg", PackageFill, packageLabel(pkg, total)))
      .addNode(boxNode("cls", ClassFill, classLabel(cls, r.sourceFile.getFileName.toString, total)))
      .addNode(boxNode("method", White, methodLabel(r.methodName, total), penwidth = 2))
      .addEdge("  report -> pkg;\n")
      .addEdge("  pkg -> cls;\n")
      .addEdge("  cls -> method;\n")

    val finalState = r.methodTree.foldLeft(initial)((s, mt) =>
      new TreeRenderer(r.coveredPositions).render(mt.body, "method", "", s)
    )

    s"""digraph "${r.methodName}" {
       |  rankdir=TB;
       |  bgcolor="white";
       |  node [fontname="Helvetica", fontsize=11, color="$Border"];
       |  edge [color="$Border", arrowsize=0.7, fontname="Helvetica", fontsize=9];
       |
       |${finalState.nodes.mkString}
       |${finalState.edges.mkString}}
       |""".stripMargin
  }

  /** Immutable accumulator threaded through [[TreeRenderer.render]]. `freshId` returns the next
    * id together with a new state carrying the incremented counter; `addNode`/`addEdge` append
    * to the respective vectors.
    */
  private final case class DotState(nextId: Int, nodes: Vector[String], edges: Vector[String]) {
    def freshId: (String, DotState) = (s"t$nextId", copy(nextId = nextId + 1))
    def addNode(node: String): DotState = copy(nodes = nodes :+ node)
    def addEdge(edge: String): DotState = copy(edges = edges :+ edge)
  }

  private object DotState {
    val empty: DotState = DotState(1, Vector.empty, Vector.empty)
  }

  /** Walks a [[BranchTree]] and emits one DOT node per source node. Three cases, one each:
    *
    *   - [[BranchTree.Branch]] — a box with the construct kind ("if" / "match" / "partial" / …) and
    *     one outgoing edge per arm. Colour is derived from descendant leaves via
    *     [[BranchTree.isReached]] — uniform across all kinds, so partial functions colour the same
    *     way as ifs.
    *   - [[BranchTree.Sequence]] — siblings under one parent edge; emit each child with the same
    *     parent and edge label.
    *   - [[BranchTree.Leaf]] — terminal expression, coloured by position lookup.
    */
  private final class TreeRenderer(coveredPositions: Set[Pos]) {

    def render(
        tree: BranchTree,
        parentId: String,
        edgeLabel: String,
        state: DotState
    ): DotState = tree match {
      case b @ BranchTree.Branch(_, kind, label, arms) =>
        val (id, s1) = state.freshId
        val reached = BranchTree.isReached(b, coveredPositions)
        val fill = stateColour(reached, NodeCovered, NodeMissed, NodeUnknown)
        val s2 = s1
          .addNode(boxNode(id, fill, branchyLabel(kind, label, reached)))
          .addEdge(edgeString(parentId, id, edgeLabel))
        arms.foldLeft(s2)((s, a) => render(a.body, id, a.label, s))

      case BranchTree.Sequence(_, children) =>
        children.foldLeft(state)((s, c) => render(c, parentId, edgeLabel, s))

      case BranchTree.Leaf(pos, text) =>
        val (id, s1) = state.freshId
        val covered = coveredPositions(pos)
        val fill = stateColour(covered, LeafCovered, LeafMissed, LeafUnknown)
        s1.addNode(diamondNode(id, fill, leafLabel(text, covered)))
          .addEdge(edgeString(parentId, id, edgeLabel))
    }

    private def edgeString(parent: String, id: String, label: String): String = {
      val attr = if (label.isEmpty) "" else s""" [label="${htmlEscape(label)}"]"""
      s"  $parent -> $id$attr;\n"
    }

    /** Three-state picker: covered / missed / unknown. "Unknown" only applies when scoverage
      * reported nothing at all (no instrumentation available); otherwise it's covered or missed.
      */
    private def stateColour[B](reached: Boolean, covered: B, missed: B, unknown: B): B =
      if (coveredPositions.isEmpty) unknown
      else if (reached) covered
      else missed

    private def branchyLabel(kind: String, label: BranchTree.Expr, reached: Boolean): String =
      s"""<font point-size="11"><b>$kind</b></font>""" +
        s"""<br/><font face="Courier" point-size="11"><b>${htmlEscape(label.text)}</b></font>""" +
        s"""<br/><font point-size="10">${statusBadge(reached, "reached", "not reached")}</font>"""

    private def leafLabel(text: String, covered: Boolean): String =
      s"""<font face="Courier" point-size="12"><b>${htmlEscape(text)}</b></font>""" +
        s"""<br/><font point-size="10">${statusBadge(covered, "covered", "not reached")}</font>"""

    private def statusBadge(reached: Boolean, coveredLabel: String, missedLabel: String): String =
      stateColour(
        reached,
        covered = s"""<font color="$CovTxt"><b>$coveredLabel</b></font>""",
        missed = s"""<font color="$MisTxt"><b>$missedLabel</b></font>""",
        unknown = s"""<font color="$UnknownTxt">unknown</font>"""
      )
  }

  // ────────────────────────────────────────────────────────────────
  // DOT shape + label helpers
  // ────────────────────────────────────────────────────────────────

  private def boxNode(id: String, fill: String, label: String, penwidth: Int = 1): String =
    s"""  $id [shape=box, style="rounded,filled", fillcolor="$fill", penwidth=$penwidth, label=<$label>];\n"""

  private def diamondNode(id: String, fill: String, label: String): String =
    s"""  $id [shape=diamond, style="filled", fillcolor="$fill", penwidth=1.5, label=<$label>];\n"""

  private def reportLabel[A](r: SessionReport[A], c: BranchCounter): String = {
    val sat = r.saturationInputIndex.fold("—")(i => s"#$i")
    s"""<font point-size="14"><b>Report</b></font><br/>${counterText(
        c
      )}<br/><font point-size="10">${r.totalInputs} inputs · saturated at $sat</font>"""
  }

  private def packageLabel(pkg: String, c: BranchCounter): String =
    s"""<font point-size="11"><b>package</b></font><br/><b>${htmlEscape(pkg)}</b><br/>${counterText(
        c
      )}"""

  private def classLabel(cls: String, sourceFileName: String, c: BranchCounter): String =
    s"""<font point-size="11"><b>class</b></font><br/><b>${htmlEscape(cls)}</b>""" +
      s"""<br/><font point-size="10" face="Courier">$sourceFileName</font><br/>${counterText(c)}"""

  private def methodLabel(mth: String, c: BranchCounter): String =
    s"""<font point-size="11"><b>method</b></font><br/><font point-size="13" face="Courier"><b>${htmlEscape(
        mth
      )}</b></font><br/>${counterText(c)}"""

  private def counterText(c: BranchCounter): String = {
    val pct = if (c.total == 0) "—" else f"${c.covered * 100.0 / c.total}%.0f%%"
    s"""<font point-size="11">branches: <b>${c.covered}/${c.total}</b> ($pct)</font>"""
  }

  // ────────────────────────────────────────────────────────────────
  // SVG growth chart
  // ────────────────────────────────────────────────────────────────

  private def renderGrowthSvg[A](r: SessionReport[A]): String = {
    val width = 820; val height = 360
    val mL = 80; val mR = 40; val mT = 60; val mB = 70
    val plotW = width - mL - mR; val plotH = height - mT - mB
    val maxX = r.totalInputs.max(1)
    val maxY = r.branchesByLine.values.map(_.counter.total).sum.max(1)

    def xC(i: Int): Double = mL + (i.toDouble / maxX) * plotW
    def yC(v: Int): Double = (height - mB) - (v.toDouble / maxY) * plotH

    val pts = stepPolyline(r.growthCurve, xC, yC)

    val yTicks = (0 to maxY)
      .map { v =>
        val y = yC(v)
        s"""    <line x1="${mL - 4}" y1="$y" x2="${width - mR}" y2="$y" stroke="#E0E0E0" stroke-dasharray="2,3"/>
         |    <line x1="${mL - 4}" y1="$y" x2="$mL" y2="$y" stroke="$Border"/>
         |    <text x="${mL - 8}" y="${y + 4}" text-anchor="end" font-size="11">$v</text>""".stripMargin
      }
      .mkString("\n")

    val xs = if (maxX <= 10) (0 to maxX).toList else List(0, maxX / 4, maxX / 2, 3 * maxX / 4, maxX)
    val xTicks = xs
      .map { v =>
        val x = xC(v)
        s"""    <line x1="$x" y1="${height - mB}" x2="$x" y2="${height - mB + 4}" stroke="$Border"/>
         |    <text x="$x" y="${height - mB + 18}" text-anchor="middle" font-size="11">$v</text>""".stripMargin
      }
      .mkString("\n")

    val sat = r.saturationInputIndex
      .map { i =>
        val x = xC(i)
        s"""    <line x1="$x" y1="$mT" x2="$x" y2="${height - mB}" stroke="$Accent" stroke-width="1.5" stroke-dasharray="5,4"/>
         |    <text x="${x + 6}" y="${mT + 14}" font-size="11" fill="$Accent">saturated at #$i</text>""".stripMargin
      }
      .getOrElse("")

    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" font-family="Helvetica, Arial, sans-serif">
       |  <rect width="$width" height="$height" fill="white"/>
       |  <text x="${width / 2}" y="32" text-anchor="middle" font-size="17" font-weight="bold">${r.methodName} — coverage growth</text>
       |  <text x="${width / 2}" y="${height - 22}" text-anchor="middle" font-size="12">input index</text>
       |  <text transform="translate(24, ${height / 2}) rotate(-90)" text-anchor="middle" font-size="12">JVM branches covered (cumulative)</text>
       |  <line x1="$mL" y1="$mT" x2="$mL" y2="${height - mB}" stroke="$Border"/>
       |  <line x1="$mL" y1="${height - mB}" x2="${width - mR}" y2="${height - mB}" stroke="$Border"/>
       |$yTicks
       |$xTicks
       |  <polyline points="$pts" fill="none" stroke="$ChartLine" stroke-width="2"/>
       |$sat
       |</svg>
       |""".stripMargin
  }

  /** Build a step-shaped polyline for the discrete growth curve. Folds `(prevY, partials)` —
    * `prevY` tracks the y of the previous point so we emit a horizontal step before each
    * level change; `partials` accumulates the `x,y` pairs that ultimately get space-joined.
    */
  private def stepPolyline(curve: Vector[Int], xC: Int => Double, yC: Int => Double): String = {
    val (_, parts) = curve.zipWithIndex.foldLeft((yC(0), Vector.empty[String])) {
      case ((prevY, acc), (cov, i)) =>
        val x = xC(i)
        val y = yC(cov)
        val first = Option.when(i == 0)(f"$x%.1f,$prevY%.1f").toVector
        val step = Option.when(y != prevY)(f"$x%.1f,$prevY%.1f").toVector
        (y, acc ++ first ++ step :+ f"$x%.1f,$y%.1f")
    }
    parts.mkString(" ")
  }

  // ────────────────────────────────────────────────────────────────
  // Summary, CSV, JSON
  // ────────────────────────────────────────────────────────────────

  private def renderSummary[A](r: SessionReport[A]): String = {
    val total = r.sourceBranchCounter
    val pct = if (total.total == 0) "—" else f"${total.covered * 100.0 / total.total}%.1f%%"
    val sat = r.saturationInputIndex.fold("never reached")(i => s"input #$i")
    val rows = r.branchesByLine.toSeq
      .sortBy(_._1)
      .map { case (line, s) =>
        val first = s.firstHitInputIndex.fold("never")(i => s"#$i")
        f"  - line $line: ${s.counter.covered}%d/${s.counter.total}%d covered, ${s.hitCount}%d hits, first reach $first"
      }
      .mkString("\n")
    s"""${r.methodName} — coverage session summary
       |${"=" * 60}
       |Source file:      ${r.sourceFile.getFileName}
       |Total inputs:     ${r.totalInputs}
       |Source branches:  ${total.covered}/${total.total} ($pct)   [from scoverage]
       |Saturated at:     $sat
       |
       |JVM branches per line (from JaCoCo — drives per-input feedback, not the headline):
       |$rows
       |""".stripMargin
  }

  private def renderInputsCsv[A](r: SessionReport[A]): String = {
    val rows = r.inputLog.iterator
      .map { rec =>
        val inputCell = csvEscape(displayInput(rec.input))
        s"${rec.index},$inputCell,${rec.linesExercised.size},${rec.linesExercised.toSeq.sorted.mkString(";")}"
      }
      .mkString("\n")
    s"index,input,exercisedLineCount,exercisedLines\n$rows\n"
  }

  private def renderJson[A](r: SessionReport[A]): String = {
    val branches = r.branchesByLine.toSeq
      .sortBy(_._1)
      .map { case (line, s) =>
        val first = s.firstHitInputIndex.fold("null")(_.toString)
        s"""    {"line": $line, "covered": ${s.counter.covered}, "total": ${s.counter.total}, "hitCount": ${s.hitCount}, "firstHitInputIndex": $first}"""
      }
      .mkString(",\n")
    val inputs = r.inputLog.iterator
      .map { rec =>
        val inputStr = jsonEscape(displayInput(rec.input))
        s"""    {"index": ${rec.index}, "input": "$inputStr", "lines": [${rec.linesExercised.toSeq.sorted
            .mkString(", ")}]}"""
      }
      .mkString(",\n")
    val sat = r.saturationInputIndex.fold("null")(_.toString)
    s"""{
       |  "method": "${r.methodName}",
       |  "sourceFile": "${r.sourceFile.getFileName}",
       |  "totalInputs": ${r.totalInputs},
       |  "saturationInputIndex": $sat,
       |  "branches": [
       |$branches
       |  ],
       |  "growthCurve": [${r.growthCurve.mkString(", ")}],
       |  "inputs": [
       |$inputs
       |  ]
       |}
       |""".stripMargin
  }

  // ────────────────────────────────────────────────────────────────
  // Helpers
  // ────────────────────────────────────────────────────────────────

  private def displayInput[A](a: A): String = {
    val s = String.valueOf(a)
    if (s.length <= InputDisplayMax) s else s.take(InputDisplayMax - 1) + "…"
  }

  private def csvEscape(s: String): String =
    if (s.contains(',') || s.contains('"') || s.contains('\n'))
      "\"" + s.replace("\"", "\"\"") + "\""
    else s

  private def jsonEscape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  private def htmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
