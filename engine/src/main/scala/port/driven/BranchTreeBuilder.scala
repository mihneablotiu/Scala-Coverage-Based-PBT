package port.driven

import cats.effect.IO
import domain.MethodTree

import java.nio.file.Path

/** Static analysis of a Scala source file: returns the [[MethodTree]] of the named method — its
  * enclosing package, class and branchy body — or `None` if the method is not found.
  */
trait BranchTreeBuilder {
  def build(sourceFile: Path, methodName: String): IO[Option[MethodTree]]
}
