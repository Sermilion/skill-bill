package skillbill.contracts.packaged

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException

internal fun loadPackagedYamlRootMapping(document: String, notMappingMessage: String): Map<*, *> =
  parsePackagedYaml(document) as? Map<*, *>
    ?: throw PackagedYamlMappingFailure(notMappingMessage)

private fun parsePackagedYaml(document: String): Any? = try {
  Yaml().load<Any?>(document)
} catch (error: CancellationException) {
  throw error
} catch (error: VirtualMachineError) {
  throw error
} catch (_: YAMLException) {
  null
} catch (_: ClassCastException) {
  null
}

internal class PackagedYamlMappingFailure(message: String) : RuntimeException(message)

internal fun Any?.packagedPositiveInt(fieldLabel: String, reject: (String) -> Nothing): Int {
  return positiveIntOrNull() ?: reject("$fieldLabel must be a positive integer")
}

internal fun Any?.packagedPositiveLong(fieldLabel: String, reject: (String) -> Nothing): Long {
  return positiveLongOrNull() ?: reject("$fieldLabel must be a positive integer")
}

internal fun requireUniqueStringItems(items: List<String>, fieldLabel: String, reject: (String) -> Nothing) {
  if (items.size != items.toSet().size) {
    reject("$fieldLabel declares duplicate entries")
  }
}

private fun Any?.positiveIntOrNull(): Int? = when (this) {
  is Int -> takeIf { it >= 1 }
  is Long -> takeIf { it in 1..Int.MAX_VALUE }?.toInt()
  is BigInteger -> runCatching { intValueExact() }.getOrNull()?.takeIf { it >= 1 }
  is BigDecimal -> runCatching { intValueExact() }.getOrNull()?.takeIf { it >= 1 }
  is Double, is Float -> (this as Number).positiveIntFromFloatingPoint()
  else -> null
}

private fun Number.positiveIntFromFloatingPoint(): Int? {
  val value = toDouble()
  val finite = value.isFinite()
  val inRange = value >= 1.0 && value <= Int.MAX_VALUE.toDouble()
  val integral = value == value.toLong().toDouble()
  return value.takeIf { finite && inRange && integral }?.toInt()
}

private fun Any?.positiveLongOrNull(): Long? = when (this) {
  is Long -> takeIf { it >= 1L }
  is Int -> toLong().takeIf { it >= 1L }
  is BigInteger -> runCatching { longValueExact() }.getOrNull()?.takeIf { it >= 1L }
  is BigDecimal -> runCatching { longValueExact() }.getOrNull()?.takeIf { it >= 1L }
  is Double, is Float -> (this as Number).positiveLongFromFloatingPoint()
  else -> null
}

private fun Number.positiveLongFromFloatingPoint(): Long? {
  val value = toDouble()
  if (!value.isFinite() || value < 1.0) return null
  return runCatching { BigDecimal.valueOf(value).longValueExact() }.getOrNull()
}
