package skillbill.engine.featuretask.review.core

import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.model.ParallelReviewLaneStatus
import skillbill.engine.featuretask.slot.PhaseLaunchFailureKind
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStepFacts
import skillbill.engine.featuretask.slot.PhaseStepInput
import skillbill.engine.featuretask.slot.PhaseStepOutput
import skillbill.ports.agentrun.model.AgentRunTermination
import skillbill.review.model.ParallelReviewLaneResult
import skillbill.review.model.ParallelReviewMergeResult
import skillbill.review.model.ReviewLaneReviewDisposition
import skillbill.review.parallel.ParallelReviewFindingParser
import skillbill.review.parallel.ParallelReviewMerger
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

class FeatureTaskLastCommitReviewDriver(
  private val runner: PhaseRunner,
  private val state: PhaseRunState,
) : FeatureTaskRuntimeReviewDriver {
  override fun run(request: ParallelCodeReviewRequest): ParallelCodeReviewResult {
    val base = requireNotNull(request.baseRevision) { "Last-commit review requires baseRevision." }
    val head = requireNotNull(request.headRevision) { "Last-commit review requires headRevision." }
    val output =
      runner.run(
        PhaseStepInput(
          stepName = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
          directive = request.withSelectedAgentAddons(lastCommitReviewFixPrompt(request, base, head)),
          priorValues = emptyMap(),
          operatorInstructions = null,
          facts =
            PhaseStepFacts(
              issueKey = request.activityWorkflowId?.takeIf(String::isNotBlank) ?: LAST_COMMIT_REVIEW_ISSUE_KEY,
              repoRoot = request.repoRoot,
              timeout = request.timeout,
              invokedAgentId = request.agent1Id,
              configuredAgentOverrideId = null,
              modelOverride = null,
              effortOverride = null,
              compaction = null,
              attempt = null,
              observeLaunch = false,
              briefingText = "",
            ),
          policy = LAST_COMMIT_REVIEW_POLICY,
        ),
        state,
      )
    val unsupported = output.launchFailure?.takeIf { it.kind == PhaseLaunchFailureKind.UNSUPPORTED_AGENT }
    return unsupported?.let { failedResult(request.agent1Id, it.cause) } ?: launchedResult(request.agent1Id, output)
  }

  private fun launchedResult(
    agentId: String,
    output: PhaseStepOutput,
  ): ParallelCodeReviewResult {
    launchFailureReason(output)?.let { reason -> return failedResult(agentId, reason) }
    val stdout = output.stdout.text
    val parsed = ParallelReviewFindingParser.parse(stdout)
    val merged =
      ParallelReviewMerger.merge(
        ParallelReviewLaneResult(agentId = agentId, findings = parsed.findings),
        ParallelReviewLaneResult(agentId = agentId, findings = emptyList()),
      )
    return ParallelCodeReviewResult(
      mergeResult = merged.copy(formattedOutput = stdout.ifBlank { "Review completed." }),
      lane1 =
        ParallelReviewLaneStatus(
          agentId = agentId,
          success = true,
          droppedCandidateDiagnostic = droppedCandidateDiagnostic(parsed.rejections.size, parsed.candidateCount),
          reviewDisposition =
            if (stdout.isBlank()) {
              ReviewLaneReviewDisposition.INCOMPLETE
            } else {
              ReviewLaneReviewDisposition.COMPLETE
            },
        ),
    )
  }

  private fun failedResult(
    agentId: String,
    reason: String,
  ) = ParallelCodeReviewResult(
    mergeResult = ParallelReviewMergeResult(findings = emptyList(), formattedOutput = ""),
    lane1 = ParallelReviewLaneStatus(agentId = agentId, success = false, failureReason = reason),
  )

  private fun launchFailureReason(output: PhaseStepOutput): String? =
    when (val termination = output.termination) {
      null -> "agent process failed to spawn"
      AgentRunTermination.TimedOut -> "agent timed out"
      AgentRunTermination.SpawnFailed -> "agent process failed to spawn"
      AgentRunTermination.Interrupted -> "agent was interrupted"
      is AgentRunTermination.Exited ->
        when {
          termination.code != 0 -> "agent exited with status ${termination.code}"
          output.stdout.truncated -> "agent output exceeded the retention cap before completion"
          else -> null
        }
    }

  private fun droppedCandidateDiagnostic(
    rejected: Int,
    candidateCount: Int,
  ): String? =
    if (rejected == 0) {
      null
    } else {
      "dropped $rejected of $candidateCount [F-XXX] candidate line(s)"
    }

  private fun lastCommitReviewFixPrompt(
    request: ParallelCodeReviewRequest,
    baseRevision: String,
    headRevision: String,
  ): String =
    buildString {
      appendLine("Review the last commit `$headRevision` against its first parent `$baseRevision`.")
      appendLine("Inspect with `git diff $baseRevision $headRevision` in this repository workspace.")
      appendLine("Do not use `origin/main...HEAD`, a merge base, the full feature branch, or a pre-baked diff blob.")
      appendLine("Do not launch bill-code-review, delegated review subagents, or an isolated review process.")
      appendLine("Fix every Blocker and Major finding in this same session before you emit.")
      appendLine("You may edit files. Leave Minor and Nit unfixed unless the edit is local and obvious.")
      appendLine("Do not commit, amend, reset, or stage changes; the runtime owns the review checkpoint.")
      appendLine("Criterion-gap detection remains exclusive to audit. Do not report unsatisfied acceptance criteria.")
      appendLine("Do not run `./gradlew check`, the pack collect-all gate, or `bill-code-check`; validate owns those.")
      request.specPath?.let { path ->
        appendLine("Subtask spec path: `$path`.")
      }
      appendLine("After fixes, emit remaining findings in this register shape, one per line:")
      appendLine("- [F-001] Blocker | High | path/File.kt:12 | remaining defect after your edits")
      appendLine("End with exactly one line: `verdict: approved` or `verdict: changes_requested`.")
      appendLine("Use `changes_requested` when any Blocker or Major remains; otherwise `approved`.")
      appendLine("An explicit empty findings list plus `verdict: approved` means no remaining Blocker or Major.")
    }

  private companion object {
    const val LAST_COMMIT_REVIEW_ISSUE_KEY = "code-review"
    val LAST_COMMIT_REVIEW_POLICY =
      PhaseStepPolicy(
        mutating = false,
        relaunchOnInvalidOutput = false,
        singleAgentSession = true,
        readOnlyIdle = false,
        fileMutating = true,
        generationScoped = false,
      )
  }
}
