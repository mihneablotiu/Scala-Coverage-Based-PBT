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
  }

  private def json: String = {
    val ints = pool.ints.toSeq.sorted.mkString("[", ",", "]")
    s"""{"method":${quote(method)},"sourceFile":${quote(sourceFile)},"strategy":${quote(strategy)},""" +
      s""""totalInputs":${feedback.iteration},"elapsedMillis":$elapsedMillis,""" +
      s""""pool":{"ints":$ints},""" +
      s""""statements":[${statements.map(statement).mkString(",")}]}"""
  }

  private def statement(s: Coverage.StatementTarget): String =
    s"""{"id":${s.id},"branch":${s.branch},"firstHitInput":${feedback.coveredAt.get(s.id).fold("null")(_.toString)}}"""

  private def quote(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
}
