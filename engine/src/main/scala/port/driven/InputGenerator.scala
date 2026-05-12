package port.driven

import cats.effect.IO
import domain.SessionFeedback

/** Produces the next input for a fuzz session given the cumulative feedback from prior iterations.
  * Random strategies discard the feedback; coverage-guided strategies use it to steer towards
  * unexplored branches.
  *
  * Parameterised over the input type `A` (typically backed by a ScalaCheck `Gen[A]` in the random
  * adapter). Implementations may keep internal state across calls (e.g. a PRNG seed for random, an
  * input pool for guided). The fuzz loop is single-threaded so plain mutable state is acceptable.
  */
trait InputGenerator[A] {
  def next(feedback: SessionFeedback[A]): IO[A]
}
