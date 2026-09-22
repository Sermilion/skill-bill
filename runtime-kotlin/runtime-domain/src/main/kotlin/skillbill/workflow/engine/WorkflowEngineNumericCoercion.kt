package skillbill.workflow.engine

import java.math.BigDecimal
import java.math.BigInteger

internal fun Any?.toStringOrEmpty(): String = this?.toString().orEmpty()

internal fun Any?.asExactIntOrNull(): Int? =
  asExactLongOrNull()?.let { value ->
    if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) value.toInt() else null
  }

internal fun Any?.asExactLongOrNull(): Long? =
  when (this) {
    is Byte -> toLong()
    is Short -> toLong()
    is Int -> toLong()
    is Long -> this
    is BigInteger -> runCatching { longValueExact() }.getOrNull()
    is BigDecimal -> runCatching { longValueExact() }.getOrNull()
    is String -> toLongOrNull()
    else -> null
  }
