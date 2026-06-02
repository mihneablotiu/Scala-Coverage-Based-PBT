package app

import benchmark.data.Tree
import domain.Generatable
import org.scalacheck.Gen

/** [[Generatable]] instances for SUT-specific input types. This is the extension point for new types: the built-in primitive/collection instances
  * live in [[Generatable]]; user types are wired here.
  */
object Generators {

  implicit val tree: Generatable[Tree] = {
    def grow(size: Int, value: Gen[Int]): Gen[Tree] =
      if (size <= 0) Gen.const(Tree.Leaf)
      else
        Gen.frequency(
          1 -> Gen.const(Tree.Leaf),
          3 -> Gen.zip(grow(size / 2, value), value, grow(size / 2, value)).map { case (l, v, r) => Tree.Node(l, v, r) }
        )

    def mutate(t: Tree): Gen[Tree] = t match {
      case Tree.Leaf          => Generatable[Int].arbitrary.map(v => Tree.Node(Tree.Leaf, v, Tree.Leaf))
      case Tree.Node(l, v, r) =>
        Gen.oneOf(
          Generatable[Int].mutate(v).map(w => Tree.Node(l, w, r)), // perturb a value
          mutate(l).map(l2 => Tree.Node(l2, v, r)),                // mutate left
          mutate(r).map(r2 => Tree.Node(l, v, r2)),                // mutate right
          Gen.const(Tree.Node(r, v, l)),                           // swap children
          Gen.const(Tree.Leaf)                                     // prune
        )
    }

    Generatable.instance(Gen.sized(s => grow(s.min(15), Generatable[Int].arbitrary)))(mutate)(p =>
      Gen.sized(s => grow(s.min(15), Generatable[Int].pooled(p)))
    )
  }
}
