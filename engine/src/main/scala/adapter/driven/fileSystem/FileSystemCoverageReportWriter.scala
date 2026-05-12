package adapter.driven.fileSystem

import cats.effect.IO
import domain.{BranchCounter, BranchSummary, BranchTree, MethodTree, Pos, SessionReport}
import port.driven.CoverageReportWriter

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Writes a [[SessionReport]] to disk in five complementary formats:
  *
  *   - `coverage.dot` — Graphviz tree (Report → Package → Class → Method → branch tree from
  *     Scalameta). AST nodes coloured green/red from scoverage's per-position data; grey when no
  *     scoverage info is available.
  *   - `growth.svg` — line chart of branches covered vs input index.
  *   - `summary.txt` — human summary suitable for pasting into a thesis.
  *   - `inputs.csv` — per-input log (importable into pandas / Excel).
  *   - `coverage.json` — full structured data.
  *
  * `coverage.svg` is *not* written here; produce it via `dot -Tsvg coverage.dot > coverage.svg`.
  */
object FileSystemCoverageReportWriter {

  def apply(): CoverageReportWriter = new Live

  private final class Live extends CoverageReportWriter {

    override def write(report: SessionReport, outDir: Path): IO[Unit] = for {
      _ <- IO(Files.createDirectories(outDir))
      _ <- writeFile(outDir, "coverage.dot", renderDot(report))
      _ <- writeFile(outDir, "growth.svg", renderGrowthSvg(report))
      _ <- writeFile(outDir, "summary.txt", renderSummary(report))
      _ <- writeFile(outDir, "inputs.csv", renderInputsCsv(report))
      _ <- writeFile(outDir, "coverage.json", renderJson(report))
    } yield ()

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

  private def renderDot(r: SessionReport): String = {
    val nodes = new StringBuilder
    val edges = new StringBuilder
    val total = sumCounters(r.branchesByLine.values.map(_.counter))

    val pkg = r.methodTree.map(_.packageName).filter(_.nonEmpty).getOrElse("<root>")
    val cls = r.methodTree
      .map(_.className)
      .filter(_.nonEmpty)
      .getOrElse(r.sourceFile.getFileName.toString.stripSuffix(".scala"))

    nodes.append(boxNode("report", White, reportLabel(r, total), penwidth = 2))
    nodes.append(boxNode("pkg", PackageFill, packageLabel(pkg, total)))
    nodes.append(
      boxNode("cls", ClassFill, classLabel(cls, r.sourceFile.getFileName.toString, total))
    )
    nodes.append(boxNode("method", White, methodLabel(r.methodName, total), penwidth = 2))
    edges.append("  report -> pkg;\n")
    edges.append("  pkg -> cls;\n")
    edges.append("  cls -> method;\n")

    r.methodTree.foreach { mt =>
      new TreeRenderer(r.coveredPositions).render(mt.body, "method", "", nodes, edges)
    }

    s"""digraph "${r.methodName}" {
       |  rankdir=TB;
       |  bgcolor="white";
       |  node [fontname="Helvetica", fontsize=11, color="$Border"];
       |  edge [color="$Border", arrowsize=0.7, fontname="Helvetica", fontsize=9];
       |
       |${nodes.toString}
       |${edges.toString}}
       |""".stripMargin
  }

  /** Walks a [[BranchTree]] and emits one DOT node per AST node, colouring each by a position
    * lookup against the scoverage-derived set of executed positions. No parent-status threading.
    */
  private final class TreeRenderer(coveredPositions: Set[Pos]) {
    private var counter = 0
    private def freshId(): String = { counter += 1; s"t$counter" }

    def render(
        tree: BranchTree,
        parentId: String,
        edgeLabel: String,
        nodes: StringBuilder,
        edges: StringBuilder
    ): Unit = tree match {
      case BranchTree.If(_, cond, thenT, elseT) =>
        val id = emitBranchy(parentId, edgeLabel, "if", cond, nodes, edges)
        render(thenT, id, "then", nodes, edges)
        render(elseT, id, "else", nodes, edges)

      case BranchTree.Match(_, scrut, cases) =>
        val id = emitBranchy(parentId, edgeLabel, "match", scrut, nodes, edges)
        cases.foreach(c => render(c.body, id, s"case ${c.pattern.text}", nodes, edges))

      case BranchTree.While(_, cond, body) =>
        val id = emitBranchy(parentId, edgeLabel, "while", cond, nodes, edges)
        render(body, id, "body", nodes, edges)

      case BranchTree.Leaf(pos, text) =>
        val id = freshId()
        val fill = pick(pos, LeafCovered, LeafMissed, LeafUnknown)
        nodes.append(diamondNode(id, fill, leafLabel(text, pos)))
        edge(parentId, id, edgeLabel, edges)
    }

    private def emitBranchy(
        parentId: String,
        edgeLabel: String,
        kind: String,
        cond: BranchTree.Expr,
        nodes: StringBuilder,
        edges: StringBuilder
    ): String = {
      val id = freshId()
      val fill = pick(cond.pos, NodeCovered, NodeMissed, NodeUnknown)
      nodes.append(boxNode(id, fill, branchyLabel(kind, cond)))
      edge(parentId, id, edgeLabel, edges)
      id
    }

    private def edge(parent: String, id: String, label: String, edges: StringBuilder): Unit = {
      val attr = if (label.isEmpty) "" else s""" [label="${htmlEscape(label)}"]"""
      edges.append(s"  $parent -> $id$attr;\n")
    }

    /** Pick one of three values based on the coverage state at `pos`. */
    private def pick[A](pos: Pos, covered: A, missed: A, unknown: A): A =
      if (coveredPositions.isEmpty) unknown
      else if (coveredPositions(pos)) covered
      else missed

    private def branchyLabel(kind: String, cond: BranchTree.Expr): String =
      s"""<font point-size="11"><b>$kind</b></font>""" +
        s"""<br/><font face="Courier" point-size="11"><b>${htmlEscape(cond.text)}</b></font>""" +
        s"""<br/><font point-size="10">${statusBadge(cond.pos, "reached", "not reached")}</font>"""

    private def leafLabel(text: String, pos: Pos): String =
      s"""<font face="Courier" point-size="12"><b>${htmlEscape(text)}</b></font>""" +
        s"""<br/><font point-size="10">${statusBadge(pos, "covered", "not reached")}</font>"""

    private def statusBadge(pos: Pos, coveredLabel: String, missedLabel: String): String =
      pick(
        pos,
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

  private def reportLabel(r: SessionReport, c: BranchCounter): String = {
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

  private def renderGrowthSvg(r: SessionReport): String = {
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
       |  <text transform="translate(24, ${height / 2}) rotate(-90)" text-anchor="middle" font-size="12">branches covered</text>
       |  <line x1="$mL" y1="$mT" x2="$mL" y2="${height - mB}" stroke="$Border"/>
       |  <line x1="$mL" y1="${height - mB}" x2="${width - mR}" y2="${height - mB}" stroke="$Border"/>
       |$yTicks
       |$xTicks
       |  <polyline points="$pts" fill="none" stroke="$ChartLine" stroke-width="2"/>
       |$sat
       |</svg>
       |""".stripMargin
  }

  /** Build a step-shaped polyline for the discrete growth curve. */
  private def stepPolyline(curve: Vector[Int], xC: Int => Double, yC: Int => Double): String = {
    val sb = new StringBuilder
    var prevY = yC(0)
    curve.zipWithIndex.foreach { case (cov, i) =>
      val x = xC(i); val y = yC(cov)
      if (i == 0) sb.append(f"$x%.1f,$prevY%.1f ")
      if (y != prevY) sb.append(f"$x%.1f,$prevY%.1f ")
      sb.append(f"$x%.1f,$y%.1f ")
      prevY = y
    }
    sb.toString.trim
  }

  // ────────────────────────────────────────────────────────────────
  // Summary, CSV, JSON
  // ────────────────────────────────────────────────────────────────

  private def renderSummary(r: SessionReport): String = {
    val total = sumCounters(r.branchesByLine.values.map(_.counter))
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
       |Total branches:   ${total.total}
       |Covered:          ${total.covered}/${total.total} ($pct)
       |Saturated at:     $sat
       |
       |Per branch (line-level):
       |$rows
       |""".stripMargin
  }

  private def renderInputsCsv(r: SessionReport): String = {
    val rows = r.inputLog.iterator
      .map { rec =>
        s"${rec.index},${rec.input},${rec.linesExercised.size},${rec.linesExercised.toSeq.sorted.mkString(";")}"
      }
      .mkString("\n")
    s"index,input,exercisedLineCount,exercisedLines\n$rows\n"
  }

  private def renderJson(r: SessionReport): String = {
    val branches = r.branchesByLine.toSeq
      .sortBy(_._1)
      .map { case (line, s) =>
        val first = s.firstHitInputIndex.fold("null")(_.toString)
        s"""    {"line": $line, "covered": ${s.counter.covered}, "total": ${s.counter.total}, "hitCount": ${s.hitCount}, "firstHitInputIndex": $first}"""
      }
      .mkString(",\n")
    val inputs = r.inputLog.iterator
      .map { rec =>
        s"""    {"index": ${rec.index}, "input": ${rec.input}, "lines": [${rec.linesExercised.toSeq.sorted
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

  private def sumCounters(cs: Iterable[BranchCounter]): BranchCounter =
    cs.foldLeft(BranchCounter(0, 0))((a, b) =>
      BranchCounter(a.covered + b.covered, a.total + b.total)
    )

  private def htmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
