package pbt.targeting

trait NumericFields[A] {
  def fields(value: A): Option[Vector[BigDecimal]]
}

trait OptionalNumericFields[A] {
  def instance: Option[NumericFields[A]]
}

object NumericFields {
  def apply[A](implicit fields: NumericFields[A]): NumericFields[A] = fields

  implicit val intFields: NumericFields[Int] =
    value => Some(Vector(BigDecimal(value)))

  implicit val doubleFields: NumericFields[Double] =
    value => Option.when(value.isFinite)(Vector(BigDecimal.decimal(value)))

  implicit def tuple2Fields[A, B](implicit A: NumericFields[A], B: NumericFields[B]): NumericFields[(A, B)] = { case (a, b) =>
    for {
      left  <- A.fields(a)
      right <- B.fields(b)
    } yield left ++ right
  }

  implicit def tuple3Fields[A, B, C](implicit A: NumericFields[A], B: NumericFields[B], C: NumericFields[C]): NumericFields[(A, B, C)] = {
    case (a, b, c) =>
      for {
        first  <- A.fields(a)
        second <- B.fields(b)
        third  <- C.fields(c)
      } yield first ++ second ++ third
  }
}

object OptionalNumericFields extends LowPriorityOptionalNumericFields {
  def apply[A](implicit fields: OptionalNumericFields[A]): OptionalNumericFields[A] = fields

  implicit def available[A](implicit fields: NumericFields[A]): OptionalNumericFields[A] =
    new OptionalNumericFields[A] {
      def instance: Option[NumericFields[A]] = Some(fields)
    }
}

trait LowPriorityOptionalNumericFields {
  implicit def missing[A]: OptionalNumericFields[A] =
    new OptionalNumericFields[A] {
      def instance: Option[NumericFields[A]] = None
    }
}
