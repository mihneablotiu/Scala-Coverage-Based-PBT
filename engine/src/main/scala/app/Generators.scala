package app

import benchmark.data.Tree
import org.scalacheck.{Arbitrary, Gen}
import pbt.gen.{ConstantPool, Generatable}

object Generators {

  implicit val int: Generatable[Int] = new Generatable[Int] {
    def arbitrary: Gen[Int]                          = Arbitrary.arbInt.arbitrary
    def mutate(seed: Int): Gen[Int]                  = Gen.oneOf(seed + 1, seed - 1, -seed, 0, 1, -1, Int.MaxValue, Int.MinValue)
    def pooled(pool: ConstantPool): Option[Gen[Int]] = Option.when(pool.ints.nonEmpty)(Gen.oneOf(pool.ints))
  }

  implicit def list[A](implicit A: Generatable[A], ordering: Ordering[A]): Generatable[List[A]] = new Generatable[List[A]] {
    def arbitrary: Gen[List[A]]             = Gen.listOf(A.arbitrary)
    def mutate(seed: List[A]): Gen[List[A]] = seed match {
      case Nil => A.arbitrary.map(List(_))
      case xs  =>
        Gen.oneOf(
          Gen.zip(index(xs), A.arbitrary).map { case (i, a) => xs.updated(i, a) },
          index(xs).flatMap(i => A.mutate(xs(i)).map(a => xs.updated(i, a))),
          index(xs).map(i => xs.patch(i, Nil, 1)),
          A.arbitrary.map(_ :: xs),
          A.arbitrary.map(xs :+ _),
          Gen.const(xs.reverse),
          Gen.const(xs.sorted),
          Gen.const(xs.sorted(ordering.reverse))
        )
    }
    def pooled(pool: ConstantPool): Option[Gen[List[A]]] = A.pooled(pool).map(Gen.listOf(_))

    private def index(xs: List[A]): Gen[Int] = Gen.choose(0, xs.length - 1)
  }

  implicit def option[A](implicit A: Generatable[A]): Generatable[Option[A]] = new Generatable[Option[A]] {
    def arbitrary: Gen[Option[A]]               = Gen.option(A.arbitrary)
    def mutate(seed: Option[A]): Gen[Option[A]] = seed match {
      case None    => A.arbitrary.map(Some(_))
      case Some(a) => Gen.oneOf(Gen.const(None), A.mutate(a).map(Some(_)))
    }
    def pooled(pool: ConstantPool): Option[Gen[Option[A]]] = A.pooled(pool).map(Gen.option(_))
  }

  implicit def tree[A](implicit A: Generatable[A], ordering: Ordering[A]): Generatable[Tree[A]] = new Generatable[Tree[A]] {
    private def shaped(value: Gen[A]): Gen[Tree[A]] = {
      def grow(size: Int): Gen[Tree[A]] =
        if (size <= 0) Gen.const(Tree.Leaf)
        else
          Gen.frequency(1 -> Gen.const(Tree.Leaf), 3 -> Gen.zip(grow(size / 2), value, grow(size / 2)).map { case (l, v, r) => Tree.Node(l, v, r) })
      Gen.sized(s => grow(s.min(15)))
    }

    def arbitrary: Gen[Tree[A]]                          = shaped(A.arbitrary)
    def pooled(pool: ConstantPool): Option[Gen[Tree[A]]] = A.pooled(pool).map(shaped)

    def mutate(seed: Tree[A]): Gen[Tree[A]] = seed match {
      case Tree.Leaf          => A.arbitrary.map(v => Tree.Node(Tree.Leaf, v, Tree.Leaf))
      case Tree.Node(l, v, r) =>
        Gen.frequency(
          3 -> mutate(l).map(Tree.Node(_, v, r)),
          3 -> mutate(r).map(Tree.Node(l, v, _)),
          2 -> A.mutate(v).map(w => Tree.Node(l, w, r)),
          1 -> Gen.const(Tree.Node(r, v, l)),
          1 -> Gen.const(Tree.Leaf),
          1 -> Gen.const(orderValues(seed, ordering)),
          1 -> Gen.const(orderValues(seed, ordering.reverse))
        )
    }

    private def orderValues(t: Tree[A], ord: Ordering[A]): Tree[A] = {
      val sorted = values(t).sorted(ord).iterator
      rebuild(t, sorted)
    }

    private def values(t: Tree[A]): List[A] = t match {
      case Tree.Leaf          => Nil
      case Tree.Node(l, v, r) => values(l) ::: v :: values(r)
    }

    private def rebuild(t: Tree[A], values: Iterator[A]): Tree[A] = t match {
      case Tree.Leaf          => Tree.Leaf
      case Tree.Node(l, _, r) => Tree.Node(rebuild(l, values), values.next(), rebuild(r, values))
    }
  }

  implicit def tuple2[A, B](implicit A: Generatable[A], B: Generatable[B]): Generatable[(A, B)] = new Generatable[(A, B)] {
    def arbitrary: Gen[(A, B)]            = Gen.zip(A.arbitrary, B.arbitrary)
    def mutate(seed: (A, B)): Gen[(A, B)] =
      Gen.oneOf(A.mutate(seed._1).map((_, seed._2)), B.mutate(seed._2).map((seed._1, _)))
    def pooled(pool: ConstantPool): Option[Gen[(A, B)]] =
      for {
        a <- A.pooled(pool)
        b <- B.pooled(pool)
      } yield Gen.zip(a, b)
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
      def pooled(pool: ConstantPool): Option[Gen[(A, B, C)]] =
        for {
          a <- A.pooled(pool)
          b <- B.pooled(pool)
          c <- C.pooled(pool)
        } yield Gen.zip(a, b, c)
    }
}
