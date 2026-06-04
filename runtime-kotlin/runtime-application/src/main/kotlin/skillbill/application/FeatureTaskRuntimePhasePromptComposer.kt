package skillbill.application

import skillbill.application.model.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

/**
 * Pure composer of the full prompt a feature-task-runtime phase agent receives. The persisted
 * per-phase briefing is the durable handoff record; this prompt is the delivered copy of it,
 * framed with the phase task directive and the phase-output contract the schema gate enforces.
 * Without this delivery the agent would receive the default goal-continuation prompt and could
 * never produce schema-valid phase output.
 */
object FeatureTaskRuntimePhasePromptComposer {
  fun compose(issueKey: String, briefing: FeatureTaskRuntimePhaseLaunchBriefing): String {
    require(issueKey.isNotBlank()) { "issueKey is required to compose a phase prompt." }
    return listOf(
      header(issueKey, briefing.phaseId),
      briefing.briefingText,
      outputContract(briefing.phaseId),
    ).joinToString(separator = "\n\n")
  }

  private fun header(issueKey: String, phaseId: String): String {
    val label = FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepLabels[phaseId] ?: phaseId
    val directive = phaseDirectives[phaseId] ?: error("No phase directive for runtime phase '$phaseId'.")
    return """
      You are executing exactly one phase of the EXPERIMENTAL skill-bill feature-task-runtime
      loop (preplan -> plan -> implement -> review -> audit -> validate -> write_history -> commit_push -> pr)
      for issue $issueKey. The runtime owns the loop; do not run other phases, do not open
      or continue any other skill-bill workflow, and do not call `skill-bill workflow continue`.

      Phase: $phaseId ($label)
      Task: $directive
    """.trimIndent()
  }

  private fun outputContract(phaseId: String): String = """
    ## Required final output (validated schema gate)
    End your response with exactly one JSON object as the last thing you emit. Prefer a raw
    object with nothing after it; a single ```json fenced block is also accepted. The runtime
    extracts that object and blocks the run if it does not validate against the phase-output
    contract:
    - "contract_version": must be exactly "${FeatureTaskRuntimePhaseWorkflowDefinition.definition.contractVersion}"
    - "phase_id": must be "$phaseId"
    - "status": one of "completed", "blocked", "failed"
    - "summary": non-empty string describing what this phase did
    - "produced_outputs": object with at least one entry carrying this phase's concrete
      result for downstream phases (for example plan steps, changed files, findings, or
      validation results)
    - "derived_notes": optional; when present, a non-empty string of notes for downstream
      phases
    No other top-level fields are allowed.
  """.trimIndent()

  // One imperative task directive per phase; the briefing carries the spec-specific scope.
  private val phaseDirectives: Map<String, String> = mapOf(
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN to
      "Produce a pre-planning digest covering scope, affected boundaries, risks/unknowns, " +
      "and rollout need. Do not modify repository files during this phase. Emit a " +
      "schema-valid produced_outputs object containing the digest for the downstream plan phase.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN to
      "Produce an ordered implementation plan that satisfies every acceptance criterion, using " +
      "the upstream preplan digest as planning context. Do not modify repository files during " +
      "this phase.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT to
      "Execute the upstream plan output: make the repository changes it describes.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW to
      "Review the implemented changes against the acceptance criteria and report defects " +
      "with concrete file references.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to
      "Verify every acceptance criterion is concretely satisfied by the implemented changes " +
      "and report any gaps.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE to
      "Run the repository validation gate relevant to the change and report the pass/fail " +
      "results.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY to
      "Invoke bill-boundary-history inline and apply its write/skip rules for the implemented " +
      "runtime change. Emit a produced_outputs object containing history_result with whether " +
      "history was written or skipped and the affected path when written.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH to
      "Stage and commit the implemented, reviewed, audited, validated, and history-updated " +
      "changes on the resolved feature branch, then push the branch. Emit commit_push_result " +
      "with the commit SHA, branch name, and pushed status. If goal-continuation suppresses PR, " +
      "this successful phase is the terminal success signal for the goal subtask.",
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR to
      "Invoke bill-pr-description, honor any repo-native PR template, create or reuse the open " +
      "pull request for the branch idempotently, and emit pr_result with the PR URL/number, " +
      "title, and whether a new PR was created.",
  )
}
