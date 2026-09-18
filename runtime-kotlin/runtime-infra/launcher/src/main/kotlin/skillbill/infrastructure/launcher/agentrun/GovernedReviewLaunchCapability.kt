package skillbill.infrastructure.launcher.agentrun

import skillbill.infrastructure.skills.install.mcp.McpConfigFormat

data class GovernedReviewLaunchCapability(
  val governedOnlyTooling: Boolean,
  val mcpIsolation: Boolean,
  val configFormat: McpConfigFormat,
)
