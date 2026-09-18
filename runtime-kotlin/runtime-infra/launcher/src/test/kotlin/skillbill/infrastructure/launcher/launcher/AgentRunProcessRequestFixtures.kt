package skillbill.infrastructure.launcher.launcher

import skillbill.infrastructure.launcher.process.AgentRunProcessRequest
import skillbill.infrastructure.launcher.process.AgentRunProcessRequestDsl
import skillbill.infrastructure.launcher.process.agentRunProcessRequest
import java.nio.file.Path

internal fun testAgentRunProcessRequest(
  command: List<String>,
  workingDirectory: Path,
  configure: AgentRunProcessRequestDsl.() -> Unit = {},
): AgentRunProcessRequest = agentRunProcessRequest(command, workingDirectory, configure)
