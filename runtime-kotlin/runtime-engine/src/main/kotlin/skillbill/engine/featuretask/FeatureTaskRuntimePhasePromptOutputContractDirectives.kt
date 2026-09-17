package skillbill.engine.featuretask
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.goalrunner.subtaskreview.FeatureTaskRuntimeVerificationSignalKeys
import skillbill.review.model.ReviewIssueCategory
import skillbill.workflow.goal.model.GoalSubtaskCommitFocusedAccounting
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

fun outputContract(briefing: FeatureTaskRuntimePhaseLaunchBriefing, agentRunValidateFallback: Boolean): String {
  val phaseId = briefing.phaseId
  return """
    ## Required final output (validated schema gate)
    End your response with exactly one JSON object as the last thing you emit. Prefer a raw
    object with nothing after it; a single ```json fenced block is also accepted. The runtime
    extracts that object and blocks the run if it does not validate against the phase-output
    contract. Copy these field values from this briefing; do not look them up in this checkout:
    - "contract_version": must be exactly "$FEATURE_TASK_RUNTIME_CONTRACT_VERSION"
    - "phase_id": must be "$phaseId"
    - "status": one of "completed", "blocked", "failed"
    - "failure_disposition": required by the runtime when status is "blocked" or "failed"; one of
      "retryable", "non_retryable_policy_conflict", "needs_user_action", "process_failure", or
      "invalid_output". Omit it when status is "completed".
    - "summary": non-empty string describing what this phase did
    - "produced_outputs": object with at least one entry carrying this phase's concrete
      result for downstream phases (for example plan steps, changed files, findings, or
      validation results)${producedOutputsAddendum(
    briefing,
    agentRunValidateFallback,
  )}
    - "derived_notes": optional; when present, a non-empty string of notes for downstream
      phases${verdictContractLine(phaseId)}
    No top-level fields other than the ones listed above are allowed.
  """.trimIndent()
}

private fun verdictContractLine(phaseId: String): String = when (phaseId) {
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT ->
    "\n    - \"verdict\": omit for audit unless every criterion is met; never invent review-style tokens " +
      "(for example remediation_required or changes_requested). Remaining criteria belong only in " +
      "produced_outputs.value."
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS,
  ->
    "\n    - \"verdict\": optional top-level string; this verifying phase sets it to drive the " +
      "advance-vs-remediation decision — see the verifying-phase signal above"
  else -> ""
}

private fun producedOutputsAddendum(
  briefing: FeatureTaskRuntimePhaseLaunchBriefing,
  agentRunValidateFallback: Boolean,
): String {
  val phaseId = briefing.phaseId
  if (FeatureTaskRuntimePhaseWorkflowDefinition.isMutatingPhase(phaseId)) {
    return mutatingProducedOutputsAddendum(briefing, agentRunValidateFallback)
  }
  val findings = FeatureTaskRuntimeVerificationSignalKeys.REVIEW_FINDINGS
  val verdict = FeatureTaskRuntimeVerificationSignalKeys.VERDICT
  return when (phaseId) {
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN,
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN,
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE,
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD,
    -> FeatureTaskRuntimePhaseProjectionShapes.exampleFor(
      phaseId,
      agentRunValidateFallback,
    )
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ->
      "\n    - This is a VERIFYING phase: produced_outputs MUST carry a \"$findings\" array (each entry a\n" +
        "      severity/message object; an explicit empty [] affirms no Blocker or Major findings) AND/OR a\n" +
        "      top-level \"$verdict\" of \"approved\" or \"changes_requested\". A Blocker or Major finding sets\n" +
        "      \"changes_requested\" so it is fixed in this same review pass; Minor and Nit do not. Output\n" +
        "      carrying NEITHER signal fails the schema gate loudly — a prose summary alone cannot advance.\n" +
        "      Each finding's \"severity\" MUST be exactly one of blocker, major, minor, nit, and its\n" +
        "      \"issue_category\" MUST be exactly one of " +
        ReviewIssueCategory.entries.joinToString { it.wireValue } + "; any other category value is\n" +
        "      recorded as other.\n" +
        "    - produced_outputs MUST also carry \"${FeatureTaskRuntimeVerificationSignalKeys.REVIEW_RUN_ID}\": the " +
        "Review run ID your\n" +
        "      `bill-code-review` invocation reported for this pass, verbatim. It is the key that joins each\n" +
        "      finding here to the imported review run, so a finding's \"id\" plus this run id must be the same\n" +
        "      pair that review recorded. Omit it ONLY if the review genuinely reported no run id; never\n" +
        "      invent, reuse an older, or guess one." + commitFocusedAccountingAddendum()
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS ->
      "\n    - This is a VERIFYING phase: emit top-level \"$verdict\" as \"findings_verified\" or " +
        "\"no_findings_verified\" and exactly one " +
        "produced_outputs.${FeatureTaskRuntimeVerificationSignalKeys.FINDINGS_VERIFICATION_DISPOSITIONS} " +
        "entry per review finding with required census fields finding_id and disposition (verified " +
        "or rejected). Recommended optional fields per entry: reason, severity, location, message, " +
        "selected_boundary_headings (heading_id and source_path), and boundary_context_unavailable " +
        "when no eligible boundary owns the finding paths. When you cite boundary memory, copy " +
        "heading_id and source_path verbatim from that finding's boundary_catalog only — never " +
        "invent hashes or reuse another finding's catalog. Concurrent worktree dirt outside the " +
        "review scope is ignored; settle dispositions for the reviewed findings only.\n" +
        "      Required example: {\"finding_id\":\"F-001\",\"disposition\":\"verified\"}."
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT -> auditProducedOutputsAddendum()
    else -> ""
  }
}

private fun mutatingProducedOutputsAddendum(
  briefing: FeatureTaskRuntimePhaseLaunchBriefing,
  agentRunValidateFallback: Boolean,
): String {
  val phaseId = briefing.phaseId
  val reconciliationRequirement =
    if (phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT) {
      ""
    } else {
      "\n    - produced_outputs MUST include a reconciliation report: a \"reconciled_state\" object\n" +
        "      (or a \"reconciled_state\" entry) with \"reconciled\": true and concrete evidence that the\n" +
        "      changed files are at their intended target state. A status of \"completed\" with the\n" +
        "      reconciliation report missing or \"reconciled\" not true fails the schema gate loudly."
    }
  return reconciliationRequirement +
    FeatureTaskRuntimePhaseProjectionShapes.exampleFor(
      phaseId,
      agentRunValidateFallback,
    )
}

private fun commitFocusedAccountingAddendum(): String =
  "\n    - If this pass ran a DELEGATED review over a real commit sequence, produced_outputs MUST also\n" +
    "      carry \"commit_focused_accounting\" exactly as the review reported it: commit_sequence_digest\n" +
    "      (64-char lowercase hex), commit_count, lane_count, focused_commit_count,\n" +
    "      skipped_commit_count (focused + skipped == commit_count), and integration_terminal_outcome,\n" +
    "      one of " + GoalSubtaskCommitFocusedAccounting.INTEGRATION_TERMINAL_OUTCOMES.sorted()
      .joinToString() + ".\n" +
    "      Optional when the review reported them: routing_digest, focused_pair_count,\n" +
    "      skipped_pair_count, lane_bundle_sizes, lane_segment_counts, incomplete_lanes,\n" +
    "      parent_analysis_pairs, parent_analysis_bytes, integration_finding_count, and\n" +
    "      integration_skip_reason (REQUIRED when integration_terminal_outcome is\n" +
    "      ${GoalSubtaskCommitFocusedAccounting.SKIPPED_NOT_APPLICABLE}). Lanes that ended incomplete\n" +
    "      are named in incomplete_lanes; that is non-clean coverage and the integration pass never\n" +
    "      compensates for it. Identities, counts, and lane names ONLY — never a commit subject, a\n" +
    "      path, or diff text. An INLINE or non-commit-sequence pass OMITS the key entirely rather\n" +
    "      than fabricating a sequence identity; never invent or guess a digest or a count."

private fun auditProducedOutputsAddendum(): String =
  "\n    - This is a VERIFYING phase. Ignore the optional-verdict bullet above for audit completion: the " +
    "runtime reads your completed final response from produced_outputs.value. Only an explicit empty list " +
    "`[]` (ordinary whitespace or Markdown fencing allowed when the complete final response is exactly " +
    "that empty list) completes audit; any other non-blank text starts one fresh audit retry with that " +
    "text forwarded verbatim as the only criterion scope for the next audit retry.\n" +
    "      REJECTED: a whitespace-only value; nesting the remaining-criteria list only inside summary.\n" +
    "      ACCEPTED completed example root: {\"contract_version\":\"$FEATURE_TASK_RUNTIME_CONTRACT_VERSION\"," +
    "\"phase_id\":\"audit\",\"status\":\"completed\"," +
    "\"summary\":\"<one sentence>\"," +
    "\"produced_outputs\":{\"value\":\"[]\"}} when every in-scope criterion is met, or " +
    "\"produced_outputs\":{\"value\":\"- AC-002 still missing test coverage\"} when criteria remain.\n" +
    "      Repair fixable gaps in this same session before you emit the final remaining-criteria response. " +
    "Use status blocked or failed with failure_disposition when the criterion list is missing or an " +
    "external dependency prevents repair.\n" +
    auditNoEarlierAuditLine() +
    "      Inspect code and test coverage only: do not run builds, tests, or other commands as audit " +
    "evidence. Validation owns test execution and failures."
