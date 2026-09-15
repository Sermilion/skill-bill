package skillbill.review.plan

object ReviewContentMatcher {
  fun contains(content: String, signal: String): Boolean = content.contains(signal, ignoreCase = true)

  fun containsAll(content: String, signals: List<String>): Boolean = signals.all { contains(content, it) }
}
