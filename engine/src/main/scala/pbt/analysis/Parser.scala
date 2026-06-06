package pbt.analysis

import pbt.gen.ConstantPool

import java.nio.file.{Files, Path}
import scala.meta._

object Parser {

  def literalPool(sourceFile: Path, method: String): Option[ConstantPool] =
    Files
      .readString(sourceFile)
      .parse[Source]
      .toOption
      .flatMap(_.collect { case d: Defn.Def if d.name.value == method => d }.headOption)
      .map(d => mineLiterals(d.body))

  private def mineLiterals(t: Tree): ConstantPool =
    t.collect { case l: Lit => l }.foldLeft(ConstantPool.empty) {
      case (p, Lit.Int(value))    => p.copy(ints = p.ints + value)
      case (p, Lit.Double(value)) => value.toDoubleOption.fold(p)(d => p.copy(doubles = p.doubles + d))
      case (p, Lit.String(value)) => p.copy(strings = p.strings + value)
      case (p, _)                 => p
    }
}
