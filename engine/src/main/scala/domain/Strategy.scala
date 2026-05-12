package domain

/** How the fuzz loop picks each input.
  *
  *   - [[Strategy.Random]]: uniform `Int` from ScalaCheck, ignoring feedback.
  *   - [[Strategy.Guided]]: coverage-guided. Today a placeholder that prints the available feedback
  *     before delegating to random — the hook for the next iteration of the project.
  */
sealed trait Strategy

object Strategy {
  case object Random extends Strategy
  case object Guided extends Strategy
}
