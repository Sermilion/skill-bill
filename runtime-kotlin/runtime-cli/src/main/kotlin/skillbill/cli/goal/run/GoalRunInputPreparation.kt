package skillbill.cli.goal.run
import com.github.ajalt.clikt.core.UsageError
import skillbill.agentaddon.model.AgentAddonConsumer
import skillbill.agentaddon.model.HydratedAgentAddonSelection
import skillbill.cli.goal.control.inputs
import skillbill.cli.goal.control.issueKey
import skillbill.cli.goal.control.repoRoot
import skillbill.cli.goal.core.Map
import skillbill.cli.goal.core.agent
import skillbill.cli.goal.core.agentAddonSelectionJson
import skillbill.cli.goal.core.agentAddonSelectionPort
import skillbill.cli.goal.core.agentAddonSlugs
import skillbill.cli.goal.core.agentOverride
import skillbill.cli.goal.core.effectiveRepoRoot
import skillbill.cli.goal.core.executableLookup
import skillbill.cli.goal.core.externalAgentAddonSourceConfigPort
import skillbill.cli.goal.core.inputs
import skillbill.cli.goal.core.issueKey
import skillbill.cli.goal.core.receivingAgents
import skillbill.cli.goal.core.repoRoot
import skillbill.cli.goal.core.stopAfterSubtask
import skillbill.cli.goal.purge.Map
import skillbill.cli.goal.purge.inputs
import skillbill.cli.goal.purge.issueKey
import skillbill.cli.goal.purge.repoRoot
import skillbill.cli.goal.status.agent
import skillbill.cli.goal.status.agentOverride
import skillbill.cli.goal.status.inputs
import skillbill.cli.goal.status.issueKey
import skillbill.cli.goal.status.path
import skillbill.cli.goal.status.repoRoot
import skillbill.cli.kernel.agent.parseAgentAddonSelection
import skillbill.cli.kernel.agent.refuseUnavailableAgentLaunchers
import skillbill.cli.kernel.agent.requireInvokingAgentId
import skillbill.cli.kernel.agent.requireSupportedOptionalAgentId
import skillbill.model.toPath
import skillbill.ports.agentaddon.model.ExternalAgentAddonSourceConfigRequest

internal fun validateGoalRunInputs(args: GoalRunInputValidationArgs) {
  val invokedAgentId = resolveInvokedAgentId(args.agent, args.inputs.environment)
  requireSupportedOptionalAgentId(args.agentOverride, "--agent-override")
  refuseUnavailableAgentLaunchers(listOf(invokedAgentId, args.agentOverride), args.executableLookup)
  val usageError = when {
    args.issueKey == null -> "issue_key is required for goal run."
    args.stopAfterSubtask != null && args.stopAfterSubtask <= 0 ->
      "--stop-after-subtask must be a positive integer."
    args.agentAddonSlugs.isNotEmpty() && args.agentAddonSelectionJson != null ->
      "Use either --agent-addon or --agent-addon-selection-json, not both."
    else -> null
  }
  if (usageError != null) throw UsageError(usageError)
}

internal fun hydrateGoalRunAgentAddonSelection(args: GoalRunAgentAddonHydrationArgs): HydratedAgentAddonSelection {
  val persistedSelection = parseAgentAddonSelection(args.agentAddonSelectionJson)
  return if (args.agentAddonSlugs.isNotEmpty()) {
    args.agentAddonSelectionPort.resolveInitial(
      repoRoot = args.effectiveRepoRoot,
      requestedSlugs = args.agentAddonSlugs,
      consumer = AgentAddonConsumer.BILL_FEATURE,
      receivingAgentIds = args.receivingAgents,
      externalSourceRoots = args.externalAgentAddonSourceConfigPort.readExternalAgentAddonSources(
        ExternalAgentAddonSourceConfigRequest(args.inputs.userHome, args.inputs.environment),
      ).sources.map { source -> source.path.toPath() },
    )
  } else if (persistedSelection.entries.isEmpty()) {
    HydratedAgentAddonSelection()
  } else {
    args.agentAddonSelectionPort.verifyPersisted(
      persistedSelection,
      AgentAddonConsumer.BILL_FEATURE,
      args.receivingAgents,
    )
  }
}

internal fun resolveInvokedAgentId(explicitAgent: String?, environment: Map<String, String>): String =
  requireInvokingAgentId(explicitAgent, environment, "--agent")
