package port.driven

import domain.Strategy
import org.scalacheck.Gen

/** Maps a `(Strategy, Gen[A])` to a concrete [[InputGenerator]] of `A`.
  *
  * Lives next to [[InputGenerator]] because it's how the use case obtains one. The driving adapter
  * supplies the implementation — the use case must not import concrete generator adapters.
  *
  * A trait (rather than a plain `Function2`) because Scala 2.13 can't express the polymorphism
  * `[A] => (Strategy, Gen[A]) => InputGenerator[A]` as a value.
  */
trait GeneratorFactory {
  def make[A](strategy: Strategy, gen: Gen[A]): InputGenerator[A]
}
