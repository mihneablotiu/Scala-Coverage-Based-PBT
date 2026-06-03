package benchmark

/** The gradient's niche. Every hard guard compares a *computed* value or *relates* parameters, so literal injection can't help — the literal isn't
  * the input — and the branch-distance gradient must climb to it over many inputs. Nothing here is hit instantly.
  */
object NumericTargets {

  // x*x == t: the pool can inject t, but the input is x, so it must search the square root. Gradient climbs |x*x − t|.
  def squareTarget(x: Int): String =
    if (x * x == 49) "root-of-49"
    else if (x * x == 1000000) "root-of-million"
    else if (x < 0) "negative"
    else "other"

  // A 20-wide window a million up, then a divisibility step inside it. Climbing into the window is gradual.
  def narrowWindow(n: Int): String =
    if (n > 1000000 && n < 1000020)
      if (n % 7 == 0) "in-window-div7" else "in-window"
    else if (n > 0) "positive"
    else "non-positive"

  // a*b == t: a product target relating two inputs. Gradient climbs |a*b − t|.
  def product(a: Int, b: Int): String =
    if (a * b == 1000000) "product-million"
    else if (a * b < 0) "opposite-signs"
    else if (a * b == 0) "has-zero"
    else "other"

  // a*a + b*b == c*c: a Pythagorean triple over three inputs — the hardest numeric relation, but the gradient still has a handle.
  def pythagorean(a: Int, b: Int, c: Int): String =
    if (a <= 0 || b <= 0 || c <= 0) "non-positive"
    else if (a * a + b * b == c * c) "triple"
    else if (a * a + b * b < c * c) "obtuse"
    else "acute"
}
