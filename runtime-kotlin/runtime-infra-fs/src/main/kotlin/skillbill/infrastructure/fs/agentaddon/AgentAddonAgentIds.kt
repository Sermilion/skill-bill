package skillbill.infrastructure.fs.agentaddon

import skillbill.error.InvalidAgentAddonAgentIdError

object AgentAddonAgentIds {
  val supportedIds: List<String> = listOf("claude", "codex", "junie", "cursor")

  fun parse(id: String): String {
    val normalized = id.trim().lowercase()
    if (normalized !in supportedIds) {
      throw InvalidAgentAddonAgentIdError(
        agentId = id,
        reason = "Unknown agent. Supported agents: ${supportedIds.joinToString(", ")}.",
      )
    }
    return normalized
  }
}
