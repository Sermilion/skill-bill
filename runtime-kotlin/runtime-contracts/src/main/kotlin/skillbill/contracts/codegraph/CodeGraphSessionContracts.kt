package skillbill.contracts.codegraph

import skillbill.error.core.FailureWireCode
import skillbill.error.core.failureWireByValue

const val CODEGRAPH_SESSION_CONTRACT_VERSION: String = "0.1"

object CodeGraphSessionPayloadKeys {
  const val CONTRACT_VERSION: String = "contract_version"
  const val SESSION_ID: String = "session_id"
  const val REPOSITORY: String = "repository"
  const val CAPABILITY: String = "capability"
  const val TELEMETRY_DISABLED: String = "telemetry_disabled"
  const val LIFECYCLE_STATE: String = "lifecycle_state"
  const val DEGRADATION_REASON: String = "degradation_reason"
  const val DETAIL: String = "detail"
  const val CLEANUP_ATTEMPTED: String = "cleanup_attempted"
  const val CLEANUP_FAILURE: String = "cleanup_failure"
  const val PRIMARY_FAILURE: String = "primary_failure"
}

enum class CodeGraphDegradationReason(override val wireValue: String) : FailureWireCode {
  MISSING_CLI("missing_cli"),
  MISSING_GRAPH("missing_graph"),
  INITIALIZATION_FAILURE("initialization_failure"),
  UNSUPPORTED_COMMAND("unsupported_command"),
  UNAVAILABLE_CAPABILITY("unavailable_capability"),
  PENDING_SYNCHRONIZATION("pending_synchronization"),
  MCP_STARTUP_FAILURE("mcp_startup_failure"),
  QUERY_FAILURE("query_failure"),
  CLEANUP_FAILURE("cleanup_failure"),

  ;

  companion object {
    fun fromWire(value: String): CodeGraphDegradationReason =
      entries.failureWireByValue(value, "CodeGraphDegradationReason")
  }
}

enum class CodeGraphLifecycleState(val wireValue: String) {
  PREPARING("preparing"),
  FALLBACK("fallback"),
  READY("ready"),
  ACTIVE("active"),
  EXITED("exited"),
  FAILED("failed"),
  TIMED_OUT("timed_out"),
  CANCELLED("cancelled"),
  CLEANUP_FAILED("cleanup_failed"),
}
