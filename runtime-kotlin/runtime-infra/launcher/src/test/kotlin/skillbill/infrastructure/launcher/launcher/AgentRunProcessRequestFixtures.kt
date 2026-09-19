package skillbill.infrastructure.launcher.launcher

import skillbill.infrastructure.launcher.process.launch.AgentRunProcessRequest
import skillbill.infrastructure.launcher.process.launch.AgentRunProcessRequestDsl
import skillbill.infrastructure.launcher.process.launch.agentRunProcessRequest
import java.nio.file.Path
internal fun testAgentRunProcessRequest(
  command: List<String>,
  workingDirectory: Path,
  configure: AgentRunProcessRequestDsl.() -> Unit = {},
): AgentRunProcessRequest = agentRunProcessRequest(command, workingDirectory, configure)
