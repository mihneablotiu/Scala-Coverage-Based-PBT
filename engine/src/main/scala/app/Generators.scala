package app

import benchmark.data.Tree
import org.scalacheck.{Arbitrary, Gen}
import pbt.gen.{ConstantPool, Generatable}
import pbt.targeting.{BranchDistance, BranchGoal, OptionalNumericFields}

object Generators {
  implicit val boolean: Generatable[Boolean] = new Generatable[Boolean] {
    override def arbitrary: Gen[Boolean] = Arbitrary.arbBool.arbitrary

    override def mutate(seed: Boolean): Gen[Boolean] = Gen.const(!seed)

    override def pooled(pool: ConstantPool): Option[Gen[Boolean]] =
      Option.when(pool.booleans.nonEmpty)(Gen.oneOf(pool.booleans))
  }

  implicit val int: Generatable[Int] = new Generatable[Int] {
    private val offsets = List(1, 2, 8, 64)

    def arbitrary: Gen[Int]         = Arbitrary.arbInt.arbitrary
    def mutate(seed: Int): Gen[Int] = {
      val anchors = List(
        0,                       // reset to the neutral integer
        1,                       // try the positive unit boundary
        -1,                      // try the negative unit boundary
        Int.MaxValue,            // jump to the upper integer edge
        Int.MinValue,            // jump to the lower integer edge
        safeInt(-seed.toLong),   // mirror the value around zero
        seed / 2,                // shrink the magnitude
        safeInt(seed.toLong * 2) // grow the magnitude
      )
      val nearby = offsets.flatMap(d =>
        List(
          safeInt(seed.toLong - d), // step below the current value
          safeInt(seed.toLong + d)  // step above the current value
        )
      )
      Gen.oneOf((anchors ++ nearby).distinct)
    }
    def pooled(pool: ConstantPool): Option[Gen[Int]]                     = Option.when(pool.ints.nonEmpty)(Gen.oneOf(pool.ints))
    override def targeted(seed: Int, goal: BranchGoal): Option[Gen[Int]] =
      targetedFrom(seed, goal, intCandidates(seed, goal))
  }

  implicit val double: Generatable[Double] = new Generatable[Double] {
    private val offsets = List(1.0, 2.0, 8.0, 64.0)

    def arbitrary: Gen[Double]            = Arbitrary.arbDouble.arbitrary
    def mutate(seed: Double): Gen[Double] = {
      val anchors = List(
        0.0,        // reset to the neutral double
        1.0,        // try the positive unit boundary
        -1.0,       // try the negative unit boundary
        -seed,      // mirror the value around zero
        seed / 2.0, // shrink the magnitude
        seed * 2.0  // grow the magnitude
      )
      val nearby = offsets.flatMap(d =>
        List(
          seed - d, // step below the current value
          seed + d  // step above the current value
        )
      )
      Gen.oneOf((anchors ++ nearby).distinct)
    }
    def pooled(pool: ConstantPool): Option[Gen[Double]]                        = Option.when(pool.doubles.nonEmpty)(Gen.oneOf(pool.doubles))
    override def targeted(seed: Double, goal: BranchGoal): Option[Gen[Double]] =
      targetedFrom(seed, goal, doubleCandidates(seed, goal))
  }

  implicit val string: Generatable[String] = new Generatable[String] {
    private val char: Gen[Char] = Gen.oneOf('a' to 'z')

    def arbitrary: Gen[String]            = Arbitrary.arbString.arbitrary
    def mutate(seed: String): Gen[String] =
      Gen.oneOf(
        Gen.const(""),                // clear the string
        char.map(_.toString + seed),  // prepend one generated character
        char.map(seed + _),           // append one generated character
        Gen.const(seed.drop(1)),      // remove the first character
        Gen.const(seed.dropRight(1)), // remove the last character
        Gen.const(seed.reverse),      // reverse character order
        Gen.const(seed.toLowerCase),  // normalize to lowercase
        Gen.const(seed.toUpperCase)   // normalize to uppercase
      )
    def pooled(pool: ConstantPool): Option[Gen[String]] = Option.when(pool.strings.nonEmpty)(Gen.oneOf(pool.strings))
  }

  implicit def list[A](implicit A: Generatable[A], ordering: Ordering[A]): Generatable[List[A]] = new Generatable[List[A]] {
    def arbitrary: Gen[List[A]]             = Gen.listOf(A.arbitrary)
    def mutate(seed: List[A]): Gen[List[A]] = seed match {
      case Nil    => Gen.oneOf(A.arbitrary.map(List(_)), arbitrary) // create a non-empty list from the empty case
      case h :: t =>
        Gen.oneOf(
          Gen.const(t),                                                             // remove the head
          A.mutate(h).map(_ :: t),                                                  // mutate the head and keep the tail
          Gen.const(List(h)),                                                       // remove the tail
          mutate(t).map(h :: _),                                                    // mutate the tail and keep the head
          A.arbitrary.map(_ :: seed),                                               // prepend one fresh element
          A.arbitrary.map(seed :+ _),                                               // append one fresh element
          index(seed).flatMap(i => A.mutate(seed(i)).map(a => seed.updated(i, a))), // mutate one element at its current index
          Gen.const(seed.reverse),                                                  // reverse the list order
          Gen.const(seed.sorted),                                                   // sort ascending
          Gen.const(seed.sorted(ordering.reverse)),                                 // sort descending
          Gen.const(seed.distinct)                                                  // keep only the first occurrence of each value
        )
    }
    def pooled(pool: ConstantPool): Option[Gen[List[A]]] =
      A.pooled(pool).map { pooled =>
        Gen.oneOf(
          pooled.map(List(_)),
          pooled.flatMap(a => Gen.listOf(A.arbitrary).map(a :: _)),
          pooled.flatMap(a => Gen.listOf(A.arbitrary).map(_ :+ a)),
          Gen.choose(1, 32).flatMap(n => Gen.listOfN(n, pooled))
        )
      }

    private def index(xs: List[A]): Gen[Int] = Gen.choose(0, xs.length - 1)
  }

  implicit def option[A](implicit A: Generatable[A]): Generatable[Option[A]] = new Generatable[Option[A]] {
    def arbitrary: Gen[Option[A]]               = Gen.option(A.arbitrary)
    def mutate(seed: Option[A]): Gen[Option[A]] = seed match {
      case None    => A.arbitrary.map(Some(_)) // introduce a generated payload
      case Some(a) =>
        Gen.oneOf(
          Gen.const(None),         // remove the payload
          A.mutate(a).map(Some(_)) // mutate the payload while keeping Some
        )
    }
    def pooled(pool: ConstantPool): Option[Gen[Option[A]]] =
      A.pooled(pool).map(_.map(Some(_)))
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
    def pooled(pool: ConstantPool): Option[Gen[Tree[A]]] =
      A.pooled(pool).map { pooled =>
        Gen.oneOf(
          pooled.map(a => Tree.Node(Tree.Leaf, a, Tree.Leaf)),
          shaped(pooled)
        )
      }

    def mutate(seed: Tree[A]): Gen[Tree[A]] = seed match {
      case Tree.Leaf =>
        Gen.oneOf(
          A.arbitrary.map(v => Tree.Node(Tree.Leaf, v, Tree.Leaf)), // create a single-node tree
          arbitrary                                                 // create an arbitrary generated tree
        )
      case Tree.Node(l, v, r) =>
        Gen.oneOf(
          Gen.const(Tree.Node(Tree.Leaf, v, r)),          // remove the left subtree
          mutate(l).map(Tree.Node(_, v, r)),              // mutate the left subtree and keep the root/right side
          Gen.const(Tree.Node(l, v, Tree.Leaf)),          // remove the right subtree
          mutate(r).map(Tree.Node(l, v, _)),              // mutate the right subtree and keep the root/left side
          arbitrary.map(Tree.Node(_, v, r)),              // attach a generated left subtree
          arbitrary.map(Tree.Node(l, v, _)),              // attach a generated right subtree
          A.mutate(v).map(w => Tree.Node(l, w, r)),       // mutate the root value
          Gen.const(Tree.Node(r, v, l)),                  // swap left and right subtrees
          Gen.const(orderValues(seed, ordering)),         // sort values ascending while preserving shape
          Gen.const(orderValues(seed, ordering.reverse)), // sort values descending while preserving shape
          Gen.const(distinctValues(seed))                 // prune duplicate-valued nodes
        )
    }

    private def distinctValues(t: Tree[A]): Tree[A] = {
      def loop(tree: Tree[A], seen: Set[A]): (Tree[A], Set[A]) = tree match {
        case Tree.Leaf          => (Tree.Leaf, seen)
        case Tree.Node(l, v, r) =>
          val (left, afterLeft) = loop(l, seen)
          if (afterLeft.contains(v)) (Tree.Leaf, afterLeft)
          else {
            val (right, afterRight) = loop(r, afterLeft + v)
            (Tree.Node(left, v, right), afterRight)
          }
      }
      loop(t, Set.empty)._1
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

  implicit def tuple2[A, B](implicit A: Generatable[A], B: Generatable[B], F: OptionalNumericFields[(A, B)]): Generatable[(A, B)] =
    new Generatable[(A, B)] {
      def arbitrary: Gen[(A, B)]            = Gen.zip(A.arbitrary, B.arbitrary)
      def mutate(seed: (A, B)): Gen[(A, B)] =
        Gen.oneOf(
          A.mutate(seed._1).map((_, seed._2)), // mutate the first component and keep the second fixed
          B.mutate(seed._2).map((seed._1, _))  // mutate the second component and keep the first fixed
        )
      def pooled(pool: ConstantPool): Option[Gen[(A, B)]] = {
        val aOnly = A.pooled(pool).map(Gen.zip(_, B.arbitrary))
        val bOnly = B.pooled(pool).map(Gen.zip(A.arbitrary, _))
        val both  = for {
          a <- A.pooled(pool)
          b <- B.pooled(pool)
        } yield Gen.zip(a, b)
        oneOf(aOnly, bOnly, both)
      }
      override def targeted(seed: (A, B), goal: BranchGoal): Option[Gen[(A, B)]] =
        Some(Gen.listOfN(16, mutate(seed)).flatMap(candidates => targetedFrom(seed, goal, candidates).getOrElse(mutate(seed))))
    }

  implicit def tuple3[A, B, C](implicit
      A: Generatable[A],
      B: Generatable[B],
      C: Generatable[C],
      F: OptionalNumericFields[(A, B, C)]
  ): Generatable[(A, B, C)] =
    new Generatable[(A, B, C)] {
      def arbitrary: Gen[(A, B, C)]               = Gen.zip(A.arbitrary, B.arbitrary, C.arbitrary)
      def mutate(seed: (A, B, C)): Gen[(A, B, C)] =
        Gen.oneOf(
          A.mutate(seed._1).map((_, seed._2, seed._3)), // mutate the first component and keep the others fixed
          B.mutate(seed._2).map((seed._1, _, seed._3)), // mutate the second component and keep the others fixed
          C.mutate(seed._3).map((seed._1, seed._2, _))  // mutate the third component and keep the others fixed
        )
      def pooled(pool: ConstantPool): Option[Gen[(A, B, C)]] = {
        val aOnly = A.pooled(pool).map(Gen.zip(_, B.arbitrary, C.arbitrary))
        val bOnly = B.pooled(pool).map(Gen.zip(A.arbitrary, _, C.arbitrary))
        val cOnly = C.pooled(pool).map(Gen.zip(A.arbitrary, B.arbitrary, _))
        val all   = for {
          a <- A.pooled(pool)
          b <- B.pooled(pool)
          c <- C.pooled(pool)
        } yield Gen.zip(a, b, c)
        oneOf(aOnly, bOnly, cOnly, all)
      }
      override def targeted(seed: (A, B, C), goal: BranchGoal): Option[Gen[(A, B, C)]] =
        Some(Gen.listOfN(16, mutate(seed)).flatMap(candidates => targetedFrom(seed, goal, candidates).getOrElse(mutate(seed))))
    }

  private def targetedFrom[A: OptionalNumericFields](seed: A, goal: BranchGoal, candidates: List[A]): Option[Gen[A]] =
    BranchDistance.distance(goal, seed).flatMap { current =>
      val ranked = candidates.distinct.flatMap(candidate => BranchDistance.distance(goal, candidate).map(candidate -> _)).sortBy(_._2)
      val useful = ranked.collect { case (candidate, distance) if distance <= current => candidate }.take(8)
      Option.when(useful.nonEmpty)(Gen.oneOf(useful))
    }

  private def intCandidates(seed: Int, goal: BranchGoal): List[Int] = {
    val anchors = List(0, 1, -1, Int.MaxValue, Int.MinValue, safeInt(-seed.toLong), seed / 2, safeInt(seed.toLong * 2))
    val nearby  = List(1, 2, 8, 64).flatMap(d => List(safeInt(seed.toLong - d), safeInt(seed.toLong + d)))
    val steps   = distanceSteps(seed, goal)
    val guided  = steps.flatMap(d => List(safeInt(BigDecimal(seed) + d), safeInt(BigDecimal(seed) - d)))
    (anchors ++ nearby ++ guided).distinct
  }

  private def doubleCandidates(seed: Double, goal: BranchGoal): List[Double] = {
    val anchors = List(0.0, 1.0, -1.0, -seed, seed / 2.0, seed * 2.0)
    val nearby  = List(1.0, 2.0, 8.0, 64.0).flatMap(d => List(seed - d, seed + d))
    val steps   = distanceSteps(seed, goal).map(_.toDouble)
    (anchors ++ nearby ++ steps.flatMap(d => List(seed - d, seed + d))).filter(_.isFinite).distinct
  }

  private def distanceSteps[A: OptionalNumericFields](seed: A, goal: BranchGoal): List[BigDecimal] =
    BranchDistance
      .distance(goal, seed)
      .toList
      .flatMap(distance => List(distance, distance / 2, distance / 4, distance / 8, distance / 16))
      .filter(_ >= 1)

  private def safeInt(value: Long): Int =
    if (value > Int.MaxValue) Int.MaxValue
    else if (value < Int.MinValue) Int.MinValue
    else value.toInt

  private def safeInt(value: BigDecimal): Int =
    if (value > Int.MaxValue) Int.MaxValue
    else if (value < Int.MinValue) Int.MinValue
    else value.toInt

  private def oneOf[A](options: Option[Gen[A]]*): Option[Gen[A]] = {
    val generators = options.flatten
    Option.when(generators.nonEmpty)(Gen.frequency(generators.map(1 -> _): _*))
  }
}
