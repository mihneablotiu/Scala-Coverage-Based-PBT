package domain

/** A method's branchy body together with its enclosing package and class names. Produced by
  * [[port.driven.BranchTreeBuilder]] from static source analysis. The writer uses the names for the
  * upper levels of the `Report → Package → Class → Method → …` tree; the method name itself is
  * already on [[SessionReport]], so it's not duplicated here.
  */
final case class MethodTree(
    packageName: String,
    className: String,
    body: BranchTree
)
