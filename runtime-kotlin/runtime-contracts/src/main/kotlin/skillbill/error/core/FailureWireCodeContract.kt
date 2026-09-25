package skillbill.error.core

import kotlin.enums.EnumEntries

interface FailureWireCode {
  val wireValue: String
}

class UnrecognizedFailureWireCodeError(
  val hierarchy: String,
  val rejectedToken: String,
) : ShellContentContractException(
    "Unrecognized failure wire code '$rejectedToken' for hierarchy '$hierarchy'.",
  )

fun <E> Array<E>.failureWireByValue(
  value: String,
  hierarchy: String,
): E where E : Enum<E>, E : FailureWireCode =
  firstOrNull { it.wireValue == value }
    ?: throw UnrecognizedFailureWireCodeError(hierarchy, value)

fun <E> EnumEntries<E>.failureWireByValue(
  value: String,
  hierarchy: String,
): E where E : Enum<E>, E : FailureWireCode =
  firstOrNull { it.wireValue == value }
    ?: throw UnrecognizedFailureWireCodeError(hierarchy, value)
