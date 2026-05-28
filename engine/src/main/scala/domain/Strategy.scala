package domain

import org.scalacheck.{Arbitrary, Gen}

/** How the fuzz loop picks each input.
  *
  * Each case carries its own `name` (which doubles as a report-folder segment — reports land in
  * `engine/reports/<SourceStem>/<method>/<strategy.name>/`). The default `gen[A]` is uniform random
  * via `Arbitrary.arbitrary[A]`; any case that needs different behaviour overrides it.
  *
  *   - [[Strategy.Random]] — uses the default; the baseline every guided strategy is compared
  *     against.
  *   - [[Strategy.MutationGuided]] — *placeholder*. Inherits the default so the per-strategy report
  *     folders exist side-by-side; only this case's `gen[A]` needs editing when a real
  *     FuzzChick-style corpus + mutation algorithm lands.
  *
  * **Adding a new strategy:** one new `case object` here, add it to [[Strategy.all]], add its
  * `name` to the Makefile's `STRATEGIES` list.
  */
sealed trait Strategy {
  def name: String
  def gen[A: Arbitrary]: Gen[A] = Arbitrary.arbitrary[A]
}

object Strategy {

  case object Random extends Strategy {
    val name = "random"
  }

  case object MutationGuided extends Strategy {
    val name = "mutation-guided"
  }

  /** Canonical list of every strategy, in the order they appear in reports and orchestrators.
    * Single source of truth for [[parse]] and the Makefile's `STRATEGIES` list — a case object that
    * isn't here can't be reached from the CLI.
    */
  val all: List[Strategy] = List(Random, MutationGuided)

  /** Resolve a strategy by its `name`, or `None` if no case matches. Case-sensitive — names are
    * meant to round-trip through file-system paths and CLI args.
    */
  def parse(name: String): Option[Strategy] = all.find(_.name == name)
}
