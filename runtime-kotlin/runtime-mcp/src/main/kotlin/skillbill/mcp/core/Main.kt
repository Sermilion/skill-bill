package skillbill.mcp.core

import skillbill.mcp.review.GovernedReviewEvidenceBridge
import skillbill.di.RuntimeComponent
import skillbill.di.create
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.create
import skillbill.model.RuntimeContext

fun main() {
  val environment = System.getenv()
  if (GovernedReviewEvidenceBridge.enabled(environment)) {
    GovernedReviewEvidenceBridge.run(environment)
  } else {
    val runtimeComponent = RuntimeComponent::class.create(RuntimeContext(environment = environment))
    val mcpComponent = McpComponent::class.create(runtimeComponent)
    McpStdioServer.run(mcpComponent)
  }
}
