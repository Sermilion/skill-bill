package skillbill.ports.codegraph

import skillbill.codegraph.model.CodeGraphLifecycleObservation
import skillbill.contracts.codegraph.CodeGraphDegradationReason
import skillbill.contracts.codegraph.CodeGraphLifecycleState
import skillbill.ports.codegraph.model.CodeGraphMcpLaunchConfiguration
import skillbill.ports.codegraph.model.CodeGraphSessionPreparation
import skillbill.ports.codegraph.model.CodeGraphSessionRequest

interface CodeGraphSessionLease : AutoCloseable {
  val launchConfiguration: CodeGraphMcpLaunchConfiguration
  val activeObservation: CodeGraphLifecycleObservation

  fun activate(): CodeGraphLifecycleObservation

  fun close(primaryFailure: Throwable? = null): CodeGraphLifecycleObservation

  fun closeWithTerminalState(
    primaryFailure: Throwable? = null,
    terminalState: CodeGraphLifecycleState,
  ): CodeGraphLifecycleObservation = close(primaryFailure)

  fun recordDegradation(reason: CodeGraphDegradationReason, detail: String): CodeGraphLifecycleObservation =
    activeObservation

  override fun close() {
    close(null)
  }
}

interface CodeGraphSessionPort {
  fun prepare(request: CodeGraphSessionRequest): CodeGraphSessionPreparation
}
