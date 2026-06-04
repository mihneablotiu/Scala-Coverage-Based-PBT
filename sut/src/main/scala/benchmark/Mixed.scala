package benchmark

/** One method, several arms — each a *different* tactic's job; only the composite covers them all, each single tactic covers just its own.
  *
  * Why it's structured this way (a real constraint, not a choice): the framework has **no crossover**, so no single branch can require two tactics at
  * once — a pool-found string can't be merged with a gradient-found number into one input. Tactics therefore compose by covering *different*
  * branches, not by combining on one. Two further rules follow: the gradient's arms must sit on top (its whole path has to stay numerically
  * expressible — a string guard above it would blind it), and **mutation isn't featured here** — it only climbs hard structural gates when
  * *concentrated* (its own strategy), and in a composite it's too diluted, so its niche stays the dedicated `Sequences` category.
  */
object Mixed {

  // gradient: the square root (n = ±7) and the negative cube root (n = -3); pool: the role strings; random: the rest.
  def triage(tag: String, n: Int): String =
    if (n * n == 49) { if (n > 0) "square-hi" else "square-lo" }
    else if (n * n * n == -27) "neg-cube"
    else if (tag == "admin") "privileged"
    else if (tag == "root") "superuser"
    else "ordinary"

  // gradient: an exact shifted difference (gated above a million) and a Pythagorean relation; pool: the command strings; random: idle.
  def dispatch(cmd: String, a: Int, b: Int): String =
    if (a > 1000000 && a - b == 1000) "shifted"
    else if (a * a + b * b == 25) "on-circle"
    else if (cmd == "start") "starting"
    else if (cmd == "stop") "stopping"
    else "idle"
}
