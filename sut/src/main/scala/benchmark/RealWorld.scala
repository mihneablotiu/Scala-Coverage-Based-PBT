package benchmark

/** Complex, branchy functions taken from well-known specs / standard-library logic — written the canonical way, NOT shaped to suit any tactic. This
  * is the external-validity check: we did not choose their control flow, so coverage here is whatever it is. Several are string parsers (deep "valid
  * parse" branches are genuinely hard for any generator); `isValidDate` is numeric, where the pool and gradient have a real handle. All are
  * deterministic, self-contained, and over types the framework can generate.
  */
object RealWorld {

  // LeetCode 8 "String to Integer (atoi)": skip spaces, optional sign, accumulate digits, clamp to Int range.
  def atoi(s: String): Int = {
    var i = 0
    val n = s.length
    while (i < n && s(i) == ' ') i += 1
    var sign = 1
    if (i < n && (s(i) == '+' || s(i) == '-')) {
      if (s(i) == '-') sign = -1
      i += 1
    }
    var result = 0L
    while (i < n && s(i) >= '0' && s(i) <= '9') {
      result = result * 10 + (s(i) - '0')
      if (sign == 1 && result > Int.MaxValue) return Int.MaxValue
      if (sign == -1 && -result < Int.MinValue) return Int.MinValue
      i += 1
    }
    (sign * result).toInt
  }

  // LeetCode 65 "Valid Number": the classic sign / digits / dot / exponent scanner — a small DFA, lots of branches.
  def isValidNumber(s: String): Boolean = {
    var i         = 0
    val n         = s.length
    var seenDigit = false
    var seenDot   = false
    var seenExp   = false
    while (i < n) {
      val c = s(i)
      if (c >= '0' && c <= '9') seenDigit = true
      else if (c == '+' || c == '-') { if (i > 0 && s(i - 1) != 'e' && s(i - 1) != 'E') return false }
      else if (c == '.') { if (seenDot || seenExp) return false; seenDot = true }
      else if (c == 'e' || c == 'E') { if (seenExp || !seenDigit) return false; seenExp = true; seenDigit = false }
      else return false
      i += 1
    }
    seenDigit
  }

  // Roman numerals → Int: per-char value (inline so the lookup branches are measured) plus the subtractive rule.
  def romanToInt(s: String): Int = {
    var total = 0
    var i     = 0
    while (i < s.length) {
      val cur = s(i) match {
        case 'I' => 1; case 'V'   => 5; case 'X'   => 10; case 'L' => 50
        case 'C' => 100; case 'D' => 500; case 'M' => 1000; case _ => 0
      }
      val nxt =
        if (i + 1 < s.length) s(i + 1) match {
          case 'I' => 1; case 'V'   => 5; case 'X'   => 10; case 'L' => 50
          case 'C' => 100; case 'D' => 500; case 'M' => 1000; case _ => 0
        }
        else 0
      if (cur < nxt) total -= cur else total += cur
      i += 1
    }
    total
  }

  // LeetCode 165 "Compare Version Numbers": dotted decimal compare, segment by segment, shorter padded with zero.
  def compareVersion(v1: String, v2: String): Int = {
    val a = v1.split('.')
    val b = v2.split('.')
    var i = 0
    val n = math.max(a.length, b.length)
    while (i < n) {
      val x = if (i < a.length && a(i).nonEmpty && a(i).forall(c => c >= '0' && c <= '9')) a(i).toLong else 0L
      val y = if (i < b.length && b(i).nonEmpty && b(i).forall(c => c >= '0' && c <= '9')) b(i).toLong else 0L
      if (x < y) return -1
      if (x > y) return 1
      i += 1
    }
    0
  }

  // IPv4 dotted-quad validation: 4 parts, each 1–3 digits, no leading zero, 0–255.
  def isValidIPv4(s: String): Boolean = {
    val parts = s.split('.')
    if (parts.length != 4) return false
    var i = 0
    while (i < 4) {
      val p = parts(i)
      if (p.isEmpty || p.length > 3) return false
      if (!p.forall(c => c >= '0' && c <= '9')) return false
      if (p.length > 1 && p(0) == '0') return false
      if (p.toInt > 255) return false
      i += 1
    }
    true
  }

  // Gregorian date validity — numeric, so the pool (month/year literals) and gradient (%, ==) have a handle: bounds, leap year, days per month.
  def isValidDate(year: Int, month: Int, day: Int): Boolean =
    if (year < 1 || year > 9999) false
    else if (month < 1 || month > 12) false
    else {
      val leap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
      val days = month match {
        case 1 | 3 | 5 | 7 | 8 | 10 | 12 => 31
        case 4 | 6 | 9 | 11              => 30
        case 2                           => if (leap) 29 else 28
        case _                           => 0
      }
      day >= 1 && day <= days
    }

  // Luhn checksum (credit-card validation): right-to-left, double every second digit, subtract 9 if >9, sum ≡ 0 (mod 10).
  def luhn(s: String): Boolean = {
    if (s.isEmpty) return false
    var sum = 0
    var alt = false
    var i   = s.length - 1
    while (i >= 0) {
      val c = s(i)
      if (c < '0' || c > '9') return false
      var d = c - '0'
      if (alt) {
        d *= 2
        if (d > 9) d -= 9
      }
      sum += d
      alt = !alt
      i -= 1
    }
    sum % 10 == 0
  }
}
