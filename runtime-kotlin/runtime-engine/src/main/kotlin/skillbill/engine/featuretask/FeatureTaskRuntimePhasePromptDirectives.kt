package skillbill.engine.featuretask
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_REPAIR_RECEIPT_CONTRACT_VERSION
import skillbill.engine.featuretask.model.FeatureTaskRuntimeImplementationContinuation
import skillbill.goalrunner.subtaskreview.FeatureTaskRuntimeVerificationSignalKeys
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewInput
import skillbill.review.context.model.CodeReviewExecutionMode
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCorrectiveRepairContext
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePriorReviewContext
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairLedger

fun implementationContinuationDirective(
  phaseId: String,
  continuation: FeatureTaskRuntimeImplementationContinuation?,
): String {
  if (continuation == null || continuation.phaseId != phaseId) return ""
  val segments = continuation.priorValueSegments.withIndex().joinToString("\n\n") { (index, value) ->
    "Segment ${index + 1} value:\n$value"
  }
  val prompt = continuation.latestPrompt?.let { "Latest optional prompt: $it" } ?: "No optional prompt recorded."
  val disposition = continuation.failureDisposition ?: "none"
  return """
    ## Continue this implementation — segment ${continuation.segmentNumber}
    A prior segment of this same implementation ran and did real work. It was NOT rejected and its
    output was NOT malformed: continue from where it stopped. Do not restart the implementation and do
    not re-apply changes already present — the mutating-phase idempotency contract still governs.

    Prior stuffed value segments:
    $segments

    $prompt
    Failure disposition from the latest segment: $disposition

    Emit a new non-blank value string carrying your updated implementation_receipt JSON stuffed inside
    value.
  """.trimIndent()
}

class PriorAttemptCorrection private constructor(
  private val reason: String,
  private val kind: Kind,
  val correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext? = null,
) {
  internal enum class Kind { SCHEMA_GATE, RETRYABLE_TERMINAL, FINDING_COVERAGE }

  val schemaGateReason: String? get() = reason.takeIf { kind == Kind.SCHEMA_GATE }
  val retryableTerminalReason: String? get() = reason.takeIf { kind == Kind.RETRYABLE_TERMINAL }
  val findingCoverageReason: String? get() = reason.takeIf { kind == Kind.FINDING_COVERAGE }

  init {
    require(correctiveRepairContext == null || kind == Kind.SCHEMA_GATE) {
      "PriorAttemptCorrection: corrective repair context belongs only to schema-gate retries, " +
        "not retryable-terminal envelopes or finding-coverage continuations."
    }
  }

  companion object {
    fun schemaGate(
      reason: String,
      correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext? = null,
    ): PriorAttemptCorrection =
      PriorAttemptCorrection(reason, Kind.SCHEMA_GATE, correctiveRepairContext = correctiveRepairContext)

    fun retryableTerminal(reason: String): PriorAttemptCorrection =
      PriorAttemptCorrection(reason, Kind.RETRYABLE_TERMINAL, correctiveRepairContext = null)

    fun unaccountedFindings(reason: String): PriorAttemptCorrection =
      PriorAttemptCorrection(reason, Kind.FINDING_COVERAGE, correctiveRepairContext = null)
  }
}

fun findingCoverageDirective(priorFindingCoverage: String?): String {
  if (priorFindingCoverage.isNullOrBlank()) return ""
  return """
    ## Findings still owed — continue this round
    Your previous attempt at this phase emitted a VALID repair receipt. It was NOT rejected and its
    format was NOT wrong. It was incomplete:
    $priorFindingCoverage
    Keep the entries you already wrote and add the missing ones. Do the repair work first, then write
    the entry that describes it. Repeating the same receipt without accounting for the named findings
    blocks the run.
  """.trimIndent()
}

fun terminalRetryDirective(priorTerminalFailure: String?): String {
  if (priorTerminalFailure.isNullOrBlank()) return ""
  return """
    ## Previous attempt reported a retryable block — try again
    Your previous attempt at this phase emitted valid output that reported the phase could not finish.
    It was NOT rejected and its format was NOT wrong. Reported reason:
    $priorTerminalFailure
    Re-attempt the phase against the current repository state. If the same obstacle still stands and you
    cannot clear it, report it again with the disposition that matches it rather than restating it in a
    different shape; a re-emitted block with no new attempt behind it will exhaust this phase's budget.
  """.trimIndent()
}

internal data class ReviewExecutionDirectiveInputs(
  val codeReviewMode: CodeReviewExecutionMode,
  val goalSubtaskReviewInput: GoalSubtaskReviewInput?,
  val reviewPassNumber: Int?,
  val resolvedReviewTier: CodeReviewExecutionMode?,
  val reviewDecidingRule: String?,
  val baselineUntrackedPaths: List<String> = emptyList(),
  val repairLedger: FeatureTaskRuntimeRepairLedger? = null,
  val priorReviewContext: FeatureTaskRuntimePriorReviewContext? = null,
)

fun commitExclusionDirective(phaseId: String): String {
  if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH) {
    return ""
  }
  return """
    ## Commit ownership
    Never amend, reset, or restage a commit this runtime does not own, including a
    commit a human operator authored: leave those alone.
  """.trimIndent()
}

fun goalContinuationDirective(phaseId: String, suppressDecomposition: Boolean): String {
  if (!suppressDecomposition || phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN) {
    return ""
  }
  return """
    ## Goal-continuation planning constraint
    This run is already executing one governed decomposed subtask. Do not propose or emit a new
    decomposition package in the plan phase. Produce implementable planning value for the current spec
    (executable_plan JSON stuffed inside value); never emit produced_outputs.decomposition_package.
    Never include installer, uninstall, or
    install-sync commands in the plan: do not plan to run
    `./install.sh`, `./uninstall.sh`, `skill-bill install`, `skill-bill install apply`, or any
    equivalent install refresh inside a goal-continuation child. The plan phase defines how future
    acceptance work will be implemented and validated; it does not require that work to have already
    happened. Never block planning merely because a later implementation or validation action is not
    yet complete. A blocked plan requires a genuinely missing input or an irreconcilable constraint
    that prevents an implementable plan from being produced.
  """.trimIndent()
}

const val AUDIT_READONLY_EVIDENCE_SENTENCE: String =
  "All evidence is read-only repository facts: never run a build, a test, or any " +
    "other command as audit evidence; validation owns test execution and failures."

private const val IMPLEMENT_READONLY_REPAIR_SENTENCE: String =
  "Repair evidence is read-only repository " +
    "facts: do not run builds or tests here."

val phaseDirectives: Map<String, String> = mapOf(
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN to
    "Produce the scaled pre-planning digest for the resolved feature size. Do not modify " +
    "repository files during this phase. Emit produced_outputs with a non-blank value string " +
    "carrying the preplanning_digest JSON (same fields as before, stuffed inside value); optional " +
    "prompt may add a short directive when non-blank. Do not forward the complete preplan envelope, " +
    "a generic summary, or progress diagnostics.",
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN to
    "Produce an ordered implementation plan that satisfies every acceptance criterion, using the " +
    "upstream preplan value as planning context (structured prose: interpret the stuffed digest " +
    "JSON). Do not modify repository files during this phase. Emit produced_outputs with a non-blank " +
    "value string carrying the executable_plan JSON (same fields as before, stuffed inside value); " +
    "optional prompt may add a short directive when non-blank. Do not forward the complete plan " +
    "envelope, a generic summary, or progress diagnostics.",
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT to
    "Reconcile the repository to the intended state the upstream plan value describes: read and " +
    "interpret the stuffed executable_plan JSON, make the changes it specifies, treating any " +
    "already-applied change as a no-op. See the mutating-phase idempotency contract below. Emit " +
    "produced_outputs with a non-blank value string carrying the implementation_receipt JSON (same " +
    "fields as before, stuffed inside value): completed_task_ids, normalized changed_paths, " +
    "tests_added, tests_updated, deviations, unresolved_items, reconciliation_evidence, and " +
    "reconciled_state. repository_checkpoint is runtime-owned: omit it and never invent a " +
    "fingerprint. Every receipt field is a bounded summary, not a transcript. " +
    IMPLEMENT_READONLY_REPAIR_SENTENCE,
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX to
    "Address every finding verify_findings carried on the CURRENT working tree as " +
    "incremental reconciliation. Every carried finding — Blocker, Major, Minor, and Nit — is in " +
    "scope; specialist narratives and raw review output are not, and a finding verification " +
    "refuted is not carried at all: do not fix it and do not file an entry for it. Do not re-apply " +
    "the plan from scratch or expand scope beyond the carried findings. Treat any fix already present " +
    "as a no-op. See the mutating-phase idempotency contract below. Emit " +
    "produced_outputs.repair_receipt with contract_version " +
    "\"$FEATURE_TASK_RUNTIME_REPAIR_RECEIPT_CONTRACT_VERSION\" and exactly one entry per carried " +
    "finding with finding_id (aliases finding_ref, id, and ref are accepted) and outcome " +
    "(addressed, no_edit_required, or attempted_unresolved). Coverage matches on finding_id and " +
    "outcome alone. Optional decoration — constructs, intent, severity, label, text, " +
    "no_edit_reason, and unresolved_reason — may accompany each entry but does not gate settlement. " +
    "A legitimately unedited finding still needs its no_edit_required entry, and a finding you " +
    "could not close needs its attempted_unresolved entry, which buys it one more attempt before it " +
    "goes to an operator. Leaving a *carried* finding out is never an outcome: the round is sent " +
    "back for it. A refuted finding is the one exception, because it was never carried.",
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW to
    "Review the last commit against its first parent in this repository. Fix every Blocker and Major " +
    "finding in this same session before you emit. Emit remaining findings and a verdict of approved or " +
    "changes_requested. Do not run bill-code-review or launch review subagents. Criterion-gap detection " +
    "remains exclusive to the audit phase. Do not run `./gradlew check`, the pack collect-all gate, or " +
    "`bill-code-check`; validate owns those.",
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS to
    "Verify every finding from the single preceding review pass against the subtask spec intent " +
    "projection and the scoped boundary-memory catalog in the briefing. Each finding receives a " +
    "titles-only heading catalog for boundaries that own its paths; select relevant heading_id " +
    "values in selected_boundary_headings and set boundary_context_unavailable when no eligible " +
    "boundary owns the finding paths. Emit envelope verdict findings_verified or " +
    "no_findings_verified and " +
    "produced_outputs.${FeatureTaskRuntimeVerificationSignalKeys.FINDINGS_VERIFICATION_DISPOSITIONS} " +
    "with exactly one {finding_id, disposition} entry per review finding (verified or rejected). " +
    "Optional decoration — reason, severity, location, message, selected_boundary_headings, and " +
    "boundary_context_unavailable — may support the disposition but does not gate settlement. Do " +
    "not edit the worktree.",
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to
    "Verify every acceptance criterion in the briefing against the current repository: locate the " +
    "implementation that provides the required behavior and test cases whose assertions verify that " +
    "behavior. Missing implementation, missing tests, and tests that do not exercise the criterion are " +
    "gaps. One meaningful test may cover several criteria; a test name, empty test, mock-only " +
    "interaction, or tautological assertion is not coverage. Read the tree at the resolved checkpoint " +
    "— the diff over its base_ref/head_ref plus its scoped_owned_paths. The upstream implement value " +
    "is structured prose (implementation_receipt JSON stuffed inside value): read and interpret it as " +
    "a producer CLAIM, not evidence. Never mark a criterion satisfied because that string lists a " +
    "completed task id, a changed path, or reconciliation_evidence claiming reconciled. Repair every " +
    "fixable gap in this same agent session, then re-check the entire in-scope criterion list from the " +
    "beginning before you complete. Do not spawn subagents, invoke repair skills, or hand findings to " +
    "another phase. End your final response with the remaining acceptance criteria only: emit an explicit " +
    "empty list `[]` when every criterion has implementation and meaningful test coverage after repairs; " +
    "otherwise emit the remaining criteria as plain text, bullets, or a numbered list without validating " +
    "their structure. Use status blocked or failed with a concrete failure_disposition when the planning " +
    "criterion list is missing or unreadable or an external dependency prevents repair. " +
    AUDIT_READONLY_EVIDENCE_SENTENCE,
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE to RUNTIME_OWNED_VALIDATE_PHASE_TASK,
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY to
    "Invoke bill-boundary-history inline and apply its write/skip rules for the implemented " +
    "runtime change. Emit a bounded history_result containing changed_paths and decisions_recorded " +
    "alongside whether history was written or skipped; do not forward implementation or validation reports.",
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH to
    "This phase does not launch an agent. The runtime stages every dirty non-ignored path, including " +
    "`.feature-specs/`, commits with a subject from the issue key and subtask name, pushes, and " +
    "records commit_sha. If goal-continuation suppresses PR, this phase is the terminal success " +
    "signal for the goal subtask.",
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR to
    "Invoke bill-pr-description, honor any repo-native PR template except its checklist, and " +
    "generate a title in the form `[<issue key>] <descriptive title>` that explains the user-visible " +
    "outcome rather than copying a branch slug; create or reuse the open pull request for the branch " +
    "idempotently, and emit pr_result with the PR URL/number, title, and whether a new PR was created.",
)

fun auditPhaseTaskDirective(): String = phaseDirectives.getValue(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT)

fun implementPhaseTaskDirective(): String =
  phaseDirectives.getValue(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT)
