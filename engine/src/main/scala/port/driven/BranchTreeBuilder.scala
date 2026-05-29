package port.driven

import domain.ParsedMethod

import java.nio.file.Path

/** Returns the named method's [[ParsedMethod]] (branch tree + leaf positions), or `None` if the method isn't found. The leaf set is computed by the
  * adapter once, at parse time.
  */
trait BranchTreeBuilder {
  def build(sourceFile: Path, methodName: String): Option[ParsedMethod]
}
