package skillbill.contracts.packaged

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.coroutines.cancellation.CancellationException

internal fun loadPackagedYamlRootMapping(
  document: String,
  notMappingMessage: String,
): Map<*, *> {
  try {
    return Yaml().load<Any?>(document) as? Map<*, *>
      ?: throw PackagedYamlMappingFailure(notMappingMessage)
  } catch (error: CancellationException) {
    throw error
  } catch (error: VirtualMachineError) {
    throw error
  } catch (_: YAMLException) {
    throw PackagedYamlMappingFailure(notMappingMessage)
  } catch (_: ClassCastException) {
    throw PackagedYamlMappingFailure(notMappingMessage)
  }
}

internal class PackagedYamlMappingFailure(message: String) : RuntimeException(message)

internal fun Any?.packagedPositiveInt(fieldLabel: String, reject: (String) -> Nothing): Int {
  if (this == null) {
    reject("$fieldLabel must be a positive integer")
  }
  return when (this) {
    is Int -> this.also { if (it < 1) reject("$fieldLabel must be a positive integer") }
    is Long -> {
      if (this < 1L || this > Int.MAX_VALUE) {
        reject("$fieldLabel must be a positive integer")
      }
      toInt()
    }
    is BigInteger -> runCatching { intValueExact() }.getOrNull()?.takeIf { it >= 1 }
      ?: reject("$fieldLabel must be a positive integer")
    is BigDecimal -> runCatching { intValueExact() }.getOrNull()?.takeIf { it >= 1 }
      ?: reject("$fieldLabel must be a positive integer")
    is Double, is Float -> {
      val doubleValue = (this as Number).toDouble()
      if (
        !doubleValue.isFinite() ||
        doubleValue < 1.0 ||
        doubleValue > Int.MAX_VALUE.toDouble() ||
        doubleValue != doubleValue.toLong().toDouble()
      ) {
        reject("$fieldLabel must be a positive integer")
      }
      doubleValue.toInt()
    }
    else -> reject("$fieldLabel must be a positive integer")
  }
}

internal fun Any?.packagedPositiveLong(fieldLabel: String, reject: (String) -> Nothing): Long {
  if (this == null) {
    reject("$fieldLabel must be a positive integer")
  }
  return when (this) {
    is Long -> this.also { if (it < 1L) reject("$fieldLabel must be a positive integer") }
    is Int -> toLong().also { if (it < 1L) reject("$fieldLabel must be a positive integer") }
    is BigInteger -> runCatching { longValueExact() }.getOrNull()?.takeIf { it >= 1L }
      ?: reject("$fieldLabel must be a positive integer")
    is BigDecimal -> runCatching { longValueExact() }.getOrNull()?.takeIf { it >= 1L }
      ?: reject("$fieldLabel must be a positive integer")
    is Double, is Float -> {
      val doubleValue = (this as Number).toDouble()
      val exactValue = runCatching { BigDecimal.valueOf(doubleValue).longValueExact() }.getOrNull()
      if (
        !doubleValue.isFinite() ||
        doubleValue < 1.0 ||
        exactValue == null
      ) {
        reject("$fieldLabel must be a positive integer")
      }
      exactValue
    }
    else -> reject("$fieldLabel must be a positive integer")
  }
}

internal fun requireUniqueStringItems(items: List<String>, fieldLabel: String, reject: (String) -> Nothing) {
  if (items.size != items.toSet().size) {
    reject("$fieldLabel declares duplicate entries")
  }
}
