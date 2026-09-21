package skillbill.infrastructure.launcher.agentrun

import com.fasterxml.jackson.databind.ObjectMapper
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.shellcontent.GovernedReviewLaunchCapabilityError
import skillbill.infrastructure.launcher.experiment.ExperimentLaunchIsolationResult
import skillbill.install.model.InstallAgent
import skillbill.ports.agentrun.model.SkillRunGoalContinuationContext
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.review.model.ReviewLaunchIsolationStrategy
import skillbill.review.context.model.launch.ReviewConversationIsolation
import java.nio.file.Path
internal fun launchPrompt(request: SkillRunRequest): String = requireNotNull(request.promptOverride) {
  "launchPrompt requires a promptOverride; goal-continuation runs spawn skill-bill directly."
}

internal fun governedReviewConversationIsolation(request: SkillRunRequest): ReviewConversationIsolation? =
  ReviewConversationIsolation.FRESH.takeIf { request.reviewEvidenceBroker != null }

internal fun requireGovernedReviewLaunch(
  request: SkillRunRequest,
  agent: InstallAgent,
  capability: GovernedReviewLaunchCapability,
) {
  if (request.reviewEvidenceEndpoint == null) return
  if (!capability.governedOnlyTooling) {
    throw GovernedReviewLaunchCapabilityError(agent.id, "governed-only tooling")
  }
  if (!capability.mcpIsolation) {
    throw GovernedReviewLaunchCapabilityError(agent.id, "MCP isolation")
  }
}

internal fun requireProcessLaunch(request: SkillRunRequest, strategy: ReviewLaunchIsolationStrategy) {
  if (request.reviewEvidenceBroker == null) return
  require(strategy.supported) {
    "Governed specialist launches require a supported fresh-context strategy."
  }
  if (strategy == ReviewLaunchIsolationStrategy.CODEX_NATIVE_FORK_TURNS_NONE) {
    require(strategy.supported) {
      "Governed Codex review launches require fresh-context isolation."
    }
  }
}

internal fun goalContinuationCommand(
  request: SkillRunRequest,
  agent: InstallAgent,
  databasePath: Path?,
  launchIsolation: ExperimentLaunchIsolationResult = experimentLaunchIsolation(request),
): AgentRunCommand? {
  val context = request.goalContinuation ?: return null
  if (request.promptOverride != null) return null
  return AgentRunCommand(
    command = goalContinuationArguments(request, agent, databasePath),
    workingDirectory = request.repoRoot,
    timeout = request.timeout,
    environment = goalContinuationEnvironment(request) + launchIsolation.environment,
    inheritEnvironment = launchIsolation.inheritEnvironment,
    idlePolicy = unstreamedLivenessPolicy(request),
  )
}

internal fun goalContinuationArguments(
  request: SkillRunRequest,
  agent: InstallAgent,
  databasePath: Path?,
): List<String> {
  val context = requireNotNull(request.goalContinuation)
  val childWorkflowId = context.childWorkflowId?.takeIf(String::isNotBlank)
  val assignedWorkflowId = context.assignedWorkflowId?.takeIf(String::isNotBlank)
  return buildList {
    add("skill-bill")
    databasePath?.let { db ->
      add("--db")
      add(db.toString())
    }
    add("feature-task")
    if (childWorkflowId != null) {
      add("resume")
      add(childWorkflowId)
    } else {
      add("run")
    }
    add(request.issueKey)
    add(context.specPath)
    if (childWorkflowId == null && assignedWorkflowId != null) {
      add("--workflow-id")
      add(assignedWorkflowId)
    }
    addGoalContinuationArguments(context)
    add("--agent")
    add(agent.id)
    request.timeout?.inWholeMinutes?.takeIf { it > 0L }?.let { minutes ->
      add("--max-wall-clock-minutes")
      add(minutes.toString())
    }
  }
}

internal fun MutableList<String>.addGoalContinuationArguments(context: SkillRunGoalContinuationContext) {
  add("--goal-parent-issue-key")
  add(context.parentIssueKey)
  add("--goal-subtask-id")
  add(context.subtaskId.toString())
  add("--goal-branch")
  add(context.goalBranch)
  add("--suppress-pr")
  addOptionalGoalIdentity(context)
  addExperimentArguments(context)
  addReviewBaselineArguments(context)
  addAddonSelectionArgument(context)
}

private fun MutableList<String>.addOptionalGoalIdentity(context: SkillRunGoalContinuationContext) {
  context.parentWorkflowId?.takeIf(String::isNotBlank)?.let { parentWorkflowId ->
    add("--goal-parent-workflow-id")
    add(parentWorkflowId)
  }
  context.lastResumableStep?.takeIf(String::isNotBlank)?.let { step ->
    add("--goal-last-resumable-step")
    add(step)
  }
  add("--code-review-mode")
  add(context.codeReviewMode.wireValue)
}

private fun MutableList<String>.addExperimentArguments(context: SkillRunGoalContinuationContext) {
  context.experimentArmId?.let { arm ->
    add("--goal-experiment-arm")
    add(arm.wireValue)
  }
  context.experimentPairId?.takeIf(String::isNotBlank)?.let { pairId ->
    add("--goal-experiment-pair-id")
    add(pairId)
  }
  if (context.experimentTreatmentCapabilities.isNotEmpty()) {
    add("--goal-experiment-treatment-capabilities")
    add(context.experimentTreatmentCapabilities.joinToString(","))
  }
  if (context.experimentRequiredLauncherCapabilities.isNotEmpty()) {
    add("--goal-experiment-required-launcher-capabilities")
    add(context.experimentRequiredLauncherCapabilities.joinToString(","))
  }
  context.experimentManagedToolsBin?.let { path ->
    add("--goal-experiment-managed-tools-bin")
    add(path.toString())
  }
  context.experimentGraphIndexDirectory?.let { path ->
    add("--goal-experiment-graph-index-directory")
    add(path.toString())
  }
}

private fun MutableList<String>.addReviewBaselineArguments(context: SkillRunGoalContinuationContext) {
  context.reviewBaseline?.let { baseline ->
    add("--goal-review-base-sha")
    add(baseline.reviewBaseSha)
    baseline.baselineUntrackedPaths.forEach { path ->
      add("--goal-baseline-untracked-path")
      add(path)
    }
  }
}

private fun MutableList<String>.addAddonSelectionArgument(context: SkillRunGoalContinuationContext) {
  if (context.agentAddonSelection.entries.isNotEmpty()) {
    add("--agent-addon-selection-json")
    add(
      ObjectMapper().writeValueAsString(
        linkedMapOf(
          SharedPayloadKeys.CONTRACT_VERSION to "0.1",
          "entries" to context.agentAddonSelection.entries.map { entry ->
            linkedMapOf(
              "slug" to entry.slug,
              "source_identity" to entry.sourceIdentity,
              "content_sha256" to entry.contentSha256,
            )
          },
        ),
      ),
    )
  }
}
