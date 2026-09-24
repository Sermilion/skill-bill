package skillbill.mcp.shared

import skillbill.di.core.OptionalCallbacks
import skillbill.di.core.RuntimeComponent
import skillbill.di.core.RuntimeContext
import skillbill.di.core.TransportContext
import skillbill.di.core.WorkflowOpsContext
import skillbill.di.core.create
import skillbill.model.EnvironmentContext
import skillbill.ports.telemetry.transport.RemoteTransportPort
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import java.nio.file.Path

data class McpRuntimeContext(
  val requester: RemoteTransportPort? = null,
  val environment: Map<String, String> = EnvironmentContext.UnspecifiedEnvironment,
  val userHome: Path = EnvironmentContext.UnspecifiedUserHome,
  val workflowGitOperations: WorkflowGitOperations? = null,
  val repositoryRoot: Path? = null,
) {
  private val component: McpComponent by lazy {
    McpComponent::class.create(RuntimeComponent::class.create(toRuntimeContext()))
  }

  fun toRuntimeContext(stdinText: String? = null): RuntimeContext =
    RuntimeContext(
      environment =
        EnvironmentContext(
          stdinText = stdinText,
          environment = environment,
          userHome = userHome,
          repositoryRoot = repositoryRoot ?: EnvironmentContext.UnspecifiedRepositoryRoot,
        ),
      transport = TransportContext(requester),
      workflowOps = WorkflowOpsContext(workflowGitOperations),
      callbacks = OptionalCallbacks(),
    )

  internal fun mcpComponent(): McpComponent = component
}
