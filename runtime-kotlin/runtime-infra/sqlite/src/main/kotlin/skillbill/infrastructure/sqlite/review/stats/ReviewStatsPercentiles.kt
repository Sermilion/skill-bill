package skillbill.infrastructure.sqlite.review.stats
import skillbill.infrastructure.sqlite.review.accounting.List
import skillbill.infrastructure.sqlite.review.review.review
import skillbill.infrastructure.sqlite.review.stage.and.review
import skillbill.infrastructure.sqlite.review.stage.finished.review
import skillbill.infrastructure.sqlite.review.stage.finished.stats
import skillbill.infrastructure.sqlite.review.stage.review
import skillbill.infrastructure.sqlite.review.stage.runtime.review
import skillbill.infrastructure.sqlite.review.stats.recorded.review
import skillbill.infrastructure.sqlite.review.stats.recorded.stats
import skillbill.infrastructure.sqlite.review.stats.task.stats
import java.util.Locale

private const val MEDIAN_PERCENTILE = 50.0
private const val P90_PERCENTILE = 90.0
private const val PERCENT_SCALE = 100.0

internal fun median(values: List<Int>): Double = percentile(values, MEDIAN_PERCENTILE)

internal fun p90(values: List<Int>): Double = percentile(values, P90_PERCENTILE)

private fun percentile(values: List<Int>, percentile: Double): Double {
  if (values.isEmpty()) {
    return 0.0
  }
  val sortedValues = values.sorted()
  val rank = (percentile / PERCENT_SCALE) * (sortedValues.size - 1)
  val lowerIndex = rank.toInt()
  val upperIndex = (lowerIndex + 1).coerceAtMost(sortedValues.lastIndex)
  val fraction = rank - lowerIndex
  val value = sortedValues[lowerIndex] + (sortedValues[upperIndex] - sortedValues[lowerIndex]) * fraction
  return String.format(Locale.US, "%.2f", value).toDouble()
}
