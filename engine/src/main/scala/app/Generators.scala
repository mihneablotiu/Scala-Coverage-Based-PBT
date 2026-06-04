package app

import benchmark.data.Tree
import org.scalacheck.Gen
import pbt.gen.{ConstantPool, Generatable}

/** [[Generatable]] instances for SUT-specific input types. The built-in primitives/collections live in [[Generatable]]; user types are wired here —
  * this single file is the template for adding any new input type (give it `arbitrary`, `mutate`, `pooled`).
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
      case Tree.Leaf          => Generatable[Int].arbitrary.map(v => Tree.Node(Tree.Leaf, v, Tree.Leaf)) // grow: a Leaf sprouts a Node
      case Tree.Node(l, v, r) =>
        // Favour recursing into a subtree (which grows depth at the leaves) over the shrinking/neutral edits, so mutation can climb deep — but keep
        // all the operators present, prune included.
        Gen.frequency(
          3 -> mutate(l).map(l2 => Tree.Node(l2, v, r)),                // mutate/grow left
          3 -> mutate(r).map(r2 => Tree.Node(l, v, r2)),                // mutate/grow right
          1 -> Generatable[Int].mutate(v).map(w => Tree.Node(l, w, r)), // perturb a value
          1 -> Gen.const(Tree.Node(r, v, l)),                           // swap children
          1 -> Gen.const(Tree.Leaf)                                     // prune
        )
    }

    def pooled(p: ConstantPool): Gen[Tree] = Gen.sized(s => grow(s.min(15), Generatable[Int].pooled(p)))

    Generatable.instance(Gen.sized(s => grow(s.min(15), Generatable[Int].arbitrary)))(mutate)(pooled)
  }
}
