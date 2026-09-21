package skillbill.codegraph.model

import skillbill.contracts.codegraph.CODEGRAPH_SESSION_CONTRACT_VERSION
import skillbill.contracts.codegraph.CodeGraphDegradationReason
import skillbill.contracts.codegraph.CodeGraphLifecycleState

private const val MAX_OBSERVATION_DETAIL_CHARS = 240

enum class CodeGraphCapability(val wireValue: String) {
  EXPLORE("explore"),
}

data class CodeGraphSessionConfiguration(
  val repository: String,
  val capability: CodeGraphCapability = CodeGraphCapability.EXPLORE,
  val telemetryDisabled: Boolean = true,
) {
  init {
    require(repository.isNotBlank()) { "repository is required." }
  }
}

data class CodeGraphDegradation(
  val reason: CodeGraphDegradationReason,
  val detail: String,
) {
  init {
    require(detail.isNotBlank()) { "CodeGraph degradation detail is required." }
    require(detail.length <= MAX_OBSERVATION_DETAIL_CHARS) { "CodeGraph degradation detail is too long." }
  }
}

data class CodeGraphLifecycleObservation(
  val sessionId: String,
  val state: CodeGraphLifecycleState,
  val configuration: CodeGraphSessionConfiguration,
  val degradation: CodeGraphDegradation? = null,
  val cleanupAttempted: Boolean = false,
  val cleanupFailure: String? = null,
  val primaryFailure: String? = null,
  val contractVersion: String = CODEGRAPH_SESSION_CONTRACT_VERSION,
) {
  init {
    require(sessionId.isNotBlank()) { "sessionId is required." }
    require(contractVersion == CODEGRAPH_SESSION_CONTRACT_VERSION) {
      "Unsupported CodeGraph session contract version '$contractVersion'."
    }
    cleanupFailure?.let { require(it.length <= MAX_OBSERVATION_DETAIL_CHARS) { "cleanupFailure is too long." } }
    primaryFailure?.let { require(it.length <= MAX_OBSERVATION_DETAIL_CHARS) { "primaryFailure is too long." } }
  }
}
