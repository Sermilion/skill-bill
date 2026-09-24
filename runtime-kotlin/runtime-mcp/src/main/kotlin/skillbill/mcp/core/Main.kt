package skillbill.mcp.core

import skillbill.di.core.OptionalCallbacks
import skillbill.di.core.RuntimeComponent
import skillbill.di.core.RuntimeContext
import skillbill.di.core.TransportContext
import skillbill.di.core.WorkflowOpsContext
import skillbill.di.core.create
import skillbill.mcp.review.GovernedReviewEvidenceBridge
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.create
import skillbill.model.EnvironmentContext

fun main() {
  val environment = System.getenv()
  if (GovernedReviewEvidenceBridge.enabled(environment)) {
    GovernedReviewEvidenceBridge.run(environment)
  } else {
    val runtimeComponent =
      RuntimeComponent::class.create(
        RuntimeContext(
          environment = EnvironmentContext(environment = environment),
          transport = TransportContext(),
          workflowOps = WorkflowOpsContext(),
          callbacks = OptionalCallbacks(),
        ),
      )
    val mcpComponent = McpComponent::class.create(runtimeComponent)
    McpStdioServer.run(mcpComponent)
  }
}
