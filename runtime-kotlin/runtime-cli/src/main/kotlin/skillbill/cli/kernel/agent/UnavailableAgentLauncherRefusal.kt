package skillbill.cli.kernel.agent
import com.github.ajalt.clikt.core.UsageError
import skillbill.install.model.unavailableAgentLauncherReason
import skillbill.ports.agentrun.ExecutableLookup

fun refuseUnavailableAgentLaunchers(
  candidateAgentIds: List<String?>,
  executableLookup: ExecutableLookup,
) {
  candidateAgentIds
    .firstNotNullOfOrNull { agentId -> unavailableAgentLauncherReason(agentId, executableLookup::onPath) }
    ?.let { reason -> throw UsageError(reason) }
}
