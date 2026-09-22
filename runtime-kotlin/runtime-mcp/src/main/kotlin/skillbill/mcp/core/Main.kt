package skillbill.mcp.core

import skillbill.di.core.RuntimeComponent
import skillbill.di.core.create
import skillbill.mcp.review.GovernedReviewEvidenceBridge
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.create
import skillbill.model.EnvironmentContext
import skillbill.model.OptionalCallbacks
import skillbill.model.RuntimeContext
import skillbill.model.TransportContext
import skillbill.model.WorkflowOpsContext

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
