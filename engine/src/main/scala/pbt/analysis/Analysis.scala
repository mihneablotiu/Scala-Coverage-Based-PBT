package pbt.analysis

import pbt.gen.ConstantPool

import java.nio.file.{Files, Path}
import scala.meta._

final case class SourceSpan(start: Int, end: Int) {
  def contains(offset: Int): Boolean = offset >= start && offset < end
}

final case class ParsedMethod(span: SourceSpan, pool: ConstantPool)

object Parser {

  def parse(sourceFile: Path, method: String): Option[ParsedMethod] =
    Files
      .readString(sourceFile)
      .parse[Source]
      .toOption
      .flatMap(_.collect { case d: Defn.Def if d.name.value == method => d }.headOption)
      .map(d => ParsedMethod(SourceSpan(d.body.pos.start, d.body.pos.end), mineLiterals(d.body)))

  private def mineLiterals(t: Tree): ConstantPool =
    t.collect { case l: Lit => l }.foldLeft(ConstantPool.empty) {
      case (p, l: Lit.Int) => p.copy(ints = p.ints + l.value)
      case (p, _)          => p
    }
}
