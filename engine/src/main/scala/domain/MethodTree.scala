package domain

/** A method's branchy body together with its enclosing package and class names, produced by
  * [[port.driven.BranchTreeBuilder]] from static source analysis. The writer uses the names for the
  * upper levels of the `Report → Package → Class → Method → …` tree.
  */
final case class MethodTree(
    packageName: String,
    className: String,
    methodName: String,
    body: BranchTree
)
