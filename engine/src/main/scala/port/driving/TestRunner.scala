package port.driving

import domain.Generatable

/** Drives one fuzz session over a SUT method. The caller declares the method, the strategy by name, and the boolean predicate. Everything else —
  * source location, output path, pool mining, strategy construction — is set up by the engine.
  */
trait TestRunner {
  def runTests[A: Generatable](
      methodName: String,
      strategyName: String
  )(property: A => Boolean): Unit
}
