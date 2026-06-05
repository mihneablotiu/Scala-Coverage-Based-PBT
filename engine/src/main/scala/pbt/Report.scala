package pbt

import pbt.gen.ConstantPool
import pbt.strategy.Feedback

import java.nio.file.{Files, Path}

final case class Report[A](
    method: String,
    sourceFile: String,
    strategy: String,
    statements: List[Coverage.StatementTarget],
    pool: ConstantPool,
    feedback: Feedback[A],
    elapsedMillis: Long
) {

  def write(outDir: Path): Unit = {
    Files.createDirectories(outDir)
    Files.writeString(outDir.resolve("coverage.json"), json)
    Files.writeString(outDir.resolve("feedback.jsonl"), feedbackJsonLines)
  }

  private val firstHits: Map[Int, Int]                           = feedback.firstHits
  private val statementsById: Map[Int, Coverage.StatementTarget] = statements.map(s => s.id -> s).toMap

  private def json: String = {
    val ints = pool.ints.toSeq.sorted.mkString("[", ",", "]")
    s"""{"method":${quote(method)},"sourceFile":${quote(sourceFile)},"strategy":${quote(strategy)},""" +
      s""""totalInputs":${feedback.iteration},"elapsedMillis":$elapsedMillis,""" +
      s""""pool":{"ints":$ints},""" +
      s""""branchTree":{"kind":"sequence","children":[${statements.map(statement).mkString(",")}]}}"""
  }

  private def statement(s: Coverage.StatementTarget): String =
    s"""{"kind":"statement","id":${s.id},"rawIds":[${s.ids.toSeq.sorted.mkString(",")}],"line":${s.line},"text":${quote(
        s.text
      )},"firstHitInput":${firstHits.get(s.id).fold("null")(_.toString)}}"""

  private def feedbackJsonLines: String =
    feedback.events
      .map { event =>
        val newStatements = event.newStatements.toSeq.sorted.flatMap(statementsById.get).map { s =>
          s"""{"id":${s.id},"line":${s.line},"text":${quote(s.text)}}"""
        }
        s"""{"iteration":${event.iteration},"input":${quote(event.input)},"newStatements":[${newStatements.mkString(
            ","
          )}],"coveredTotal":${event.coveredTotal},"targetTotal":${statements.size},"corpusSize":${event.corpusSize}}"""
      }
      .mkString("", "\n", if (feedback.events.isEmpty) "" else "\n")

  private def quote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
