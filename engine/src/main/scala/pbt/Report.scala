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
    s"""{"method":${quote(method)},"sourceFile":${quote(sourceFile)},"strategy":${quote(strategy)},""" +
      s""""totalInputs":${feedback.iteration},"elapsedMillis":$elapsedMillis,""" +
      s""""pool":${poolJson(pool)},""" +
      s""""statements":[${statements.map(statement).mkString(",")}]}"""
  }

  private def statement(s: Coverage.StatementTarget): String =
    s"""{"id":${s.id},"branch":${s.branch},"firstHitInput":${feedback.coveredAt.get(s.id).fold("null")(_.toString)}}"""

  private def poolJson(pool: ConstantPool): String = {
    val ints     = pool.ints.toSeq.sorted.mkString("[", ",", "]")
    val doubles  = pool.doubles.toSeq.sorted.mkString("[", ",", "]")
    val strings  = pool.strings.toSeq.sorted.map(quote).mkString("[", ",", "]")
    val booleans = List(false, true).filter(pool.booleans).mkString("[", ",", "]")
    s"""{"ints":$ints,"doubles":$doubles,"strings":$strings,"booleans":$booleans}"""
  }

  private def quote(s: String): String =
    "\"" + s.flatMap {
      case '\\'         => "\\\\"
      case '"'          => "\\\""
      case '\n'         => "\\n"
      case '\r'         => "\\r"
      case '\t'         => "\\t"
      case c if c < ' ' => f"\\u${c.toInt}%04x"
      case c            => c.toString
    } + "\""
}
