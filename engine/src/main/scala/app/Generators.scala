package app

import org.scalacheck.{Arbitrary, Gen}
import pbt.dataTypes.Tree
import pbt.gen.{ConstantPool, Generatable}

/** Every [[Generatable]] we support, in one place. The leaf types `Int` and `String` are concrete; the composites `List`, `Option`, `Tree`, and
  * tuples are generic and defer to their components' `arbitrary` / `mutate` / `pooled`. To support a new type, add one `implicit` here.
  */
object Generators {

  // ── Leaf types ───────────────────────────────────────────────────────

  implicit val int: Generatable[Int] = new Generatable[Int] {
    def arbitrary: Gen[Int]         = Arbitrary.arbInt.arbitrary
    def mutate(seed: Int): Gen[Int] =
      Gen.oneOf(seed + 1, seed - 1, seed + 16, seed - 16, seed + 256, seed - 256, -seed, 0, 1, -1, Int.MaxValue, Int.MinValue)
    def pooled(pool: ConstantPool): Gen[Int] = ConstantPool.inject(pool.ints, arbitrary)
  }

  implicit val string: Generatable[String] = new Generatable[String] {
    def arbitrary: Gen[String]            = Arbitrary.arbString.arbitrary
    def mutate(seed: String): Gen[String] =
      if (seed.isEmpty) char.map(_.toString)
      else
        Gen.oneOf(
          Gen.zip(index(seed), char).map { case (i, c) => seed.take(i) + c + seed.drop(i + 1) }, // substitute one char
          index(seed).map(i => seed.take(i) + seed.drop(i + 1)), // delete one char
          Gen.zip(Gen.choose(0, seed.length), char).map { case (i, c) => seed.take(i) + c + seed.drop(i) } // insert one char
        )
    def pooled(pool: ConstantPool): Gen[String] = ConstantPool.inject(pool.strings, arbitrary)

    private def char: Gen[Char]            = Arbitrary.arbChar.arbitrary
    private def index(s: String): Gen[Int] = Gen.choose(0, s.length - 1)
  }

  // ── Composite types — defer to the component generators ──────────────

  implicit def list[A](implicit A: Generatable[A]): Generatable[List[A]] = new Generatable[List[A]] {
    def arbitrary: Gen[List[A]]             = Gen.listOf(A.arbitrary)
    def mutate(seed: List[A]): Gen[List[A]] = seed match {
      case Nil         => A.arbitrary.map(_ :: Nil)
      case xs @ h :: t =>
        Gen.oneOf(
          A.mutate(h).map(_ :: t), // mutate head
          mutate(t).map(h :: _),   // mutate tail
          Gen.const(t),            // drop head
          Gen.const(Nil),          // empty out
          A.arbitrary.map(_ :: xs) // prepend a fresh element
        )
    }
    def pooled(pool: ConstantPool): Gen[List[A]] = Gen.listOf(A.pooled(pool))
  }

  implicit def option[A](implicit A: Generatable[A]): Generatable[Option[A]] = new Generatable[Option[A]] {
    def arbitrary: Gen[Option[A]]               = Gen.option(A.arbitrary)
    def mutate(seed: Option[A]): Gen[Option[A]] = seed match {
      case None    => A.arbitrary.map(Some(_))
      case Some(a) => Gen.oneOf(Gen.const(None), A.mutate(a).map(Some(_)))
    }
    def pooled(pool: ConstantPool): Gen[Option[A]] = Gen.option(A.pooled(pool))
  }

  implicit def tree[A](implicit A: Generatable[A]): Generatable[Tree[A]] = new Generatable[Tree[A]] {
    // A random-shaped tree. `arbitrary` fills it with arbitrary values; `pooled` fills the same shape with pooled values — we only ever pool the
    // values inside a tree, never the shape itself.
    private def shaped(value: Gen[A]): Gen[Tree[A]] = {
      def grow(size: Int): Gen[Tree[A]] =
        if (size <= 0) Gen.const(Tree.Leaf)
        else
          Gen.frequency(1 -> Gen.const(Tree.Leaf), 3 -> Gen.zip(grow(size / 2), value, grow(size / 2)).map { case (l, v, r) => Tree.Node(l, v, r) })
      Gen.sized(s => grow(s.min(15)))
    }

    def arbitrary: Gen[Tree[A]]                  = shaped(A.arbitrary)
    def pooled(pool: ConstantPool): Gen[Tree[A]] = shaped(A.pooled(pool))

    def mutate(seed: Tree[A]): Gen[Tree[A]] = seed match {
      case Tree.Leaf          => A.arbitrary.map(v => Tree.Node(Tree.Leaf, v, Tree.Leaf)) // a Leaf sprouts a Node
      case Tree.Node(l, v, r) =>
        // Favour recursing into a subtree (grows depth) over neutral edits, so mutation can climb deep.
        Gen.frequency(
          3 -> mutate(l).map(Tree.Node(_, v, r)),        // grow left
          3 -> mutate(r).map(Tree.Node(l, v, _)),        // grow right
          1 -> A.mutate(v).map(w => Tree.Node(l, w, r)), // perturb a value
          1 -> Gen.const(Tree.Node(r, v, l)),            // swap children
          1 -> Gen.const(Tree.Leaf)                      // prune
        )
    }
  }

  implicit def tuple2[A, B](implicit A: Generatable[A], B: Generatable[B]): Generatable[(A, B)] = new Generatable[(A, B)] {
    def arbitrary: Gen[(A, B)]            = Gen.zip(A.arbitrary, B.arbitrary)
    def mutate(seed: (A, B)): Gen[(A, B)] =
      Gen.oneOf(A.mutate(seed._1).map((_, seed._2)), B.mutate(seed._2).map((seed._1, _)))
    def pooled(pool: ConstantPool): Gen[(A, B)] = Gen.zip(A.pooled(pool), B.pooled(pool))
  }

  implicit def tuple3[A, B, C](implicit A: Generatable[A], B: Generatable[B], C: Generatable[C]): Generatable[(A, B, C)] =
    new Generatable[(A, B, C)] {
      def arbitrary: Gen[(A, B, C)]               = Gen.zip(A.arbitrary, B.arbitrary, C.arbitrary)
      def mutate(seed: (A, B, C)): Gen[(A, B, C)] =
        Gen.oneOf(
          A.mutate(seed._1).map((_, seed._2, seed._3)),
          B.mutate(seed._2).map((seed._1, _, seed._3)),
          C.mutate(seed._3).map((seed._1, seed._2, _))
        )
      def pooled(pool: ConstantPool): Gen[(A, B, C)] = Gen.zip(A.pooled(pool), B.pooled(pool), C.pooled(pool))
    }
}
