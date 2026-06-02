package domain

import org.scalacheck.{Arbitrary, Gen}

/** Everything a [[Strategy]] needs to produce inputs of type `A`:
  *   - `arbitrary` — a uniform draw (exactly ScalaCheck's `Arbitrary[A]`);
  *   - `mutate` — a "nearby" variant of a seed (AFL-style edits);
  *   - `pooled` — a fresh draw that splices in mined source literals ([[ConstantPool]]).
  *
  * One bound `[A: Generatable]` carries all three through the engine. New input types plug in by providing an instance via [[Generatable.instance]]
  * (see the built-ins below, or `app.Generators` for the `Tree` example).
  */
trait Generatable[A] {
  def arbitrary: Gen[A]
  def mutate(seed: A): Gen[A]
  def pooled(pool: ConstantPool): Gen[A]
}

object Generatable {

  def apply[A](implicit g: Generatable[A]): Generatable[A] = g

  def instance[A](arb: Gen[A])(mut: A => Gen[A])(pld: ConstantPool => Gen[A]): Generatable[A] =
    new Generatable[A] {
      def arbitrary: Gen[A]                  = arb
      def mutate(seed: A): Gen[A]            = mut(seed)
      def pooled(pool: ConstantPool): Gen[A] = pld(pool)
    }

  implicit val boolean: Generatable[Boolean] =
    instance(Arbitrary.arbBool.arbitrary)(b => Gen.const(!b))(_ => Arbitrary.arbBool.arbitrary)

  // Multi-scale steps (±2^k) so a hill-climber can both jump across large numeric gaps and refine
  // locally; the boundary/zero values stay as AFL-style "interesting" deltas.
  private val IntSteps: List[Int] = (0 to 30).map(1 << _).toList

  private def intNeighbour(n: Int): Gen[Int] =
    Gen.oneOf(IntSteps.flatMap(s => List(n + s, n - s)) ++ List(-n, 0, 1, -1, Int.MaxValue, Int.MinValue))

  implicit val int: Generatable[Int] =
    instance(Arbitrary.arbInt.arbitrary)(intNeighbour)(p => ConstantPool.inject(p.ints, Arbitrary.arbInt.arbitrary))

  implicit val long: Generatable[Long] =
    instance(Arbitrary.arbLong.arbitrary)(n => Gen.oneOf(n + 1, n - 1, -n, 0L, 1L, -1L, Long.MaxValue, Long.MinValue))(p =>
      ConstantPool.inject(p.longs, Arbitrary.arbLong.arbitrary)
    )

  /** Edge values random almost never produces (NaN, ±∞, exact 0/1) are the mutation targets; magic constants come from the pool.
    */
  implicit val double: Generatable[Double] =
    instance(Arbitrary.arbDouble.arbitrary)(x =>
      Gen.oneOf(x + 1, x - 1, -x, x * 2, 0.0, 1.0, -1.0, Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity)
    )(p => ConstantPool.inject(p.doubles, Arbitrary.arbDouble.arbitrary))

  implicit val string: Generatable[String] =
    instance(Arbitrary.arbString.arbitrary)(mutateString)(p => ConstantPool.inject(p.strings, Arbitrary.arbString.arbitrary))

  private def mutateString(s: String): Gen[String] =
    if (s.isEmpty) Arbitrary.arbChar.arbitrary.map(_.toString)
    else
      Gen.oneOf(
        Gen.zip(Gen.choose(0, s.length - 1), Arbitrary.arbChar.arbitrary).map { case (i, c) => s.take(i) + c + s.drop(i + 1) },
        Gen.choose(0, s.length - 1).map(i => s.take(i) + s.drop(i + 1)),
        Gen.zip(Gen.choose(0, s.length), Arbitrary.arbChar.arbitrary).map { case (i, c) => s.take(i) + c + s.drop(i) }
      )

  /** Generic over the element type, so `List[Int]`, `List[String]`, `List[List[Int]]`, … all derive. `lazy` because the mutation schema references
    * the instance itself for the tail.
    */
  implicit def list[A](implicit A: Generatable[A]): Generatable[List[A]] = {
    lazy val self: Generatable[List[A]] = instance[List[A]](Gen.listOf(A.arbitrary)) {
      case Nil         => A.arbitrary.map(_ :: Nil)
      case xs @ h :: t =>
        Gen.oneOf(
          A.mutate(h).map(_ :: t),    // mutate head
          self.mutate(t).map(h :: _), // mutate tail
          Gen.const(t),               // drop head
          Gen.const(Nil),             // empty out
          A.arbitrary.map(_ :: xs)    // prepend a fresh element
        )
    }(p => Gen.listOf(A.pooled(p)))
    self
  }

  implicit def option[A](implicit A: Generatable[A]): Generatable[Option[A]] =
    instance[Option[A]](Gen.option(A.arbitrary)) {
      case None    => A.arbitrary.map(Some(_))
      case Some(a) => Gen.oneOf(Gen.const(None), A.mutate(a).map(Some(_)))
    }(p => Gen.option(A.pooled(p)))

  implicit def map[K, V](implicit K: Generatable[K], V: Generatable[V]): Generatable[Map[K, V]] =
    instance[Map[K, V]](Gen.mapOf(Gen.zip(K.arbitrary, V.arbitrary))) { m =>
      if (m.isEmpty) Gen.zip(K.arbitrary, V.arbitrary).map(Map(_))
      else
        Gen.oneOf(
          Gen.oneOf(m.keys.toSeq).flatMap(k => V.mutate(m(k)).map(v => m.updated(k, v))), // mutate a value
          Gen.oneOf(m.keys.toSeq).map(k => m - k),                                        // drop an entry
          Gen.zip(K.arbitrary, V.arbitrary).map { case (k, v) => m.updated(k, v) } // add an entry
        )
    }(p => Gen.mapOf(Gen.zip(K.pooled(p), V.pooled(p))))

  implicit def tuple2[A, B](implicit A: Generatable[A], B: Generatable[B]): Generatable[(A, B)] =
    instance[(A, B)](Gen.zip(A.arbitrary, B.arbitrary)) { case (a, b) =>
      Gen.oneOf(A.mutate(a).map((_, b)), B.mutate(b).map((a, _)))
    }(p => Gen.zip(A.pooled(p), B.pooled(p)))

  implicit def tuple3[A, B, C](implicit A: Generatable[A], B: Generatable[B], C: Generatable[C]): Generatable[(A, B, C)] =
    instance[(A, B, C)](Gen.zip(A.arbitrary, B.arbitrary, C.arbitrary)) { case (a, b, c) =>
      Gen.oneOf(A.mutate(a).map((_, b, c)), B.mutate(b).map((a, _, c)), C.mutate(c).map((a, b, _)))
    }(p => Gen.zip(A.pooled(p), B.pooled(p), C.pooled(p)))
}
