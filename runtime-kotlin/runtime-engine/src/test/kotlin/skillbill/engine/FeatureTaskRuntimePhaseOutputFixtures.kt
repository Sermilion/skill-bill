package skillbill.engine
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriver
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
internal const val REVIEW_FIX_BLOCKER_FINDING_ID = "F-001"

internal var harnessPendingVerifyFindingIds: List<String> = emptyList()

internal fun harnessReviewDriverSyncingPendingVerifyFindings(
  delegate: FeatureTaskRuntimeReviewDriver,
): FeatureTaskRuntimeReviewDriver = FeatureTaskRuntimeReviewDriver { request ->
  val result = delegate.run(request)
  harnessPendingVerifyFindingIds = result.mergeResult.findings.map { it.fNumber }
  result
}

internal fun verifyFindingsPhaseOutput(
  verifiedFindingIds: List<String> = harnessPendingVerifyFindingIds,
): FeatureTaskRuntimePhaseOutput = FeatureTaskRuntimePhaseOutput(
  "verify_findings",
  1,
  verifyFindingsOutput(verifiedFindingIds),
)

internal fun verifyFindingsOutputForFindingIds(vararg findingIds: String): String =
  verifyFindingsOutput(findingIds.toList())

internal fun verifyFindingsOutput(verifiedFindingIds: List<String> = harnessPendingVerifyFindingIds): String {
  val dispositions = verifiedFindingIds.joinToString(",") { findingId ->
    """{"finding_id":"$findingId","disposition":"verified","boundary_context_unavailable":true}"""
  }
  val verdict = if (verifiedFindingIds.isEmpty()) "no_findings_verified" else "findings_verified"
  val dispositionsJson = if (dispositions.isEmpty()) "[]" else "[$dispositions]"
  return """
  {
    "contract_version": "0.6",
    "phase_id": "verify_findings",
    "status": "completed",
    "summary": "Phase produced a validated output.",
    "verdict": "$verdict",
    "produced_outputs": {"finding_dispositions": $dispositionsJson}
  }
  """.trimIndent()
}

internal val IMPLEMENT_NO_RECONCILE_OUTPUT: String = """
  {
    "contract_version": "0.6",
    "phase_id": "implement",
    "status": "completed",
    "summary": "Phase produced a validated output.",
    "produced_outputs": {
      "value": "Implement prose without a reconciliation report.",
      "changed_files": ["src/Foo.kt"]
    }
  }
""".trimIndent()

internal fun verdictPlanOutput(verdict: String): String = """
  {
    "contract_version": "0.6",
    "phase_id": "plan",
    "status": "completed",
    "summary": "Plan produced a validated output.",
    "verdict": "$verdict",
    "produced_outputs": ${validProducedOutputs("plan")}
  }
""".trimIndent()

internal val FINALISED_COMMIT_PUSH_OUTPUT: String = """
  {
    "contract_version": "0.6",
    "phase_id": "commit_push",
    "status": "completed",
    "summary": "Phase produced a validated output.",
    "produced_outputs": ${commitPushProducedOutputs(commitSha = "commit-runtime-1")}
  }
""".trimIndent()

internal fun commitPushProducedOutputs(
  commitSha: String? = null,
  changedPaths: List<String> = listOf("src/Foo.kt"),
): String {
  val sha = commitSha?.let { """"commit_sha":"$it",""" } ?: ""
  val pathsJson = changedPaths.joinToString(",") { "\"$it\"" }
  return """{"commit_push_result":{
    $sha
    "message":"SKILL-65: runtime feature-task parity",
    "changed_paths":[$pathsJson],
    "branch":"feat/SKILL-65-runtime-feature-task-parity",
    "base_branch":"main",
    "pushed":true
  }}
  """.trimIndent()
}

internal fun validJsonOutput(phaseId: String, commitPushChangedPaths: List<String>? = null): String {
  if (phaseId == "verify_findings") {
    return verifyFindingsOutput()
  }
  if (phaseId == "audit") {
    return """
    {
      "contract_version": "0.6",
      "phase_id": "audit",
      "status": "completed",
      "summary": "Phase produced a validated output.",
      "verdict": "satisfied",
      "produced_outputs": ${validProducedOutputs(phaseId, commitPushChangedPaths)}
    }
    """.trimIndent()
  }
  return """
  {
    "contract_version": "0.6",
    "phase_id": "$phaseId",
    "status": "completed",
    "summary": "Phase produced a validated output.",
    "produced_outputs": ${validProducedOutputs(phaseId, commitPushChangedPaths)}
  }
  """.trimIndent()
}

internal fun validJsonOutputForGitPhase(phaseId: String, git: RecordingWorkflowGitOperations): String = validJsonOutput(
  phaseId,
  commitPushChangedPaths = if (phaseId == "commit_push" && git.ownedPathsValue.isNotEmpty()) {
    git.ownedPathsValue
  } else {
    null
  },
)

internal fun validProducedOutputs(phaseId: String, commitPushChangedPaths: List<String>? = null): String =
  when (phaseId) {
    "validate" -> validateProducedOutputs()
    "write_history" -> WRITE_HISTORY_PRODUCED_OUTPUTS
    "commit_push" -> commitPushProducedOutputs(
      commitSha = null,
      changedPaths = commitPushChangedPaths ?: listOf("src/Foo.kt"),
    )
    "preplan" -> preplanProducedOutputs()
    "plan" -> planProducedOutputs()
    "implement" -> implementProducedOutputs()
    "implement_fix" -> implementFixProducedOutputs()
    "review" -> """{"findings": []}"""
    "audit" -> """{"value": "{\"gaps\":[],\"non_blocking_findings\":[]}"}"""
    "verify_findings" -> """{"finding_dispositions": []}"""
    else -> """{"tasks":["task-1"]}"""
  }

private fun validateProducedOutputs(): String = """{"value":"finished","validation_result":{
      "validation_status":"passed",
      "checks":["FooTest"],
      "repository_checkpoint":{"fingerprint":"fixture-checkpoint-1"},
      "gate_run_count":1,
      "gate_runs":[{"duration_ms":1,"outcome":"passed","cache_mode":"forced_full","executed_work_units":1,
        "command":"./gradlew check","exit_code":0}],
      "validation_evidence":{"contract_version":"0.1","results":[
        {"command":"./gradlew check","exit_code":0}
      ]}
    }}
""".trimIndent()

private const val WRITE_HISTORY_PRODUCED_OUTPUTS =
  """{"history_result":{"changed_paths":["agent/history.md"],"decisions_recorded":[]}}"""

private fun preplanProducedOutputs(): String = """{
      "value":"Fixture preplan prose for downstream plan."
    }
""".trimIndent()

private fun planProducedOutputs(): String = """{
      "value":"Fixture plan prose for downstream implement and audit."
    }
""".trimIndent()

private fun implementProducedOutputs(): String = """{
      "value":"Fixture implement prose for downstream audit."
    }
""".trimIndent()

private fun implementFixProducedOutputs(): String = """{
      "repair_receipt": {
        "contract_version": "0.3",
        "entries": [{
          "finding_id": "F-001",
          "outcome": "addressed"
        }]
      },
      "reconciled_state": {"reconciled": true, "evidence": "Fixture tree at target state."}
    }
""".trimIndent()

internal data class PhaseOutputFixture(
  val id: String,
  val phaseId: String,
  val producedOutputs: String,
)

internal val PLANNING_PROJECTION_FIXTURES: List<PhaseOutputFixture> = emptyList()

internal val PLANNING_PROJECTION_EXEMPT_PHASES: Set<String> =
  setOf(
    "preplan",
    "plan",
    "implement",
    "review",
    "audit",
    "verify_findings",
    "implement_fix",
    "commit_push",
  )

internal object Skill187SyntheticAuditResponses {
  const val NESTED_VERDICT_SENTINEL: String = "SKILL187-NESTED-VERDICT"
  const val OBSERVATION_SENTINEL: String = "SKILL187-BAD-OBSERVATION"
  const val ARTIFACT_SENTINEL: String = "SKILL187-OVERSIZE-ARTIFACT"
  const val YAML_NESTED_SENTINEL: String = "SKILL187-YAML-NESTED"
  const val UNSUPPORTED_YAML_SENTINEL: String = "SKILL187-UNSUPPORTED-YAML"
  private const val AUDIT_VALUE_SATISFIED: String = """{\"gaps\":[],\"non_blocking_findings\":[]}"""

  fun nestedVerdictMissingDelimiter(): String =
    """{"contract_version":"0.6","phase_id":"audit","status":"completed","summary":"$NESTED_VERDICT_SENTINEL",""" +
      """"produced_outputs":{"value":"$AUDIT_VALUE_SATISFIED","verdict":"satisfied"}"""

  fun nestedVerdictComplete(): String = nestedVerdictMissingDelimiter() + "}"

  fun nestedVerdictConservativeYaml(): String =
    "{contract_version: \"0.6\", phase_id: \"audit\", status: \"completed\", " +
      "summary: \"$YAML_NESTED_SENTINEL\", produced_outputs: {value: \"$AUDIT_VALUE_SATISFIED\", " +
      "verdict: \"satisfied\"}}"

  fun invalidCriterionShape(): String =
    """{"contract_version":"0.6","phase_id":"audit","status":"completed","summary":"$OBSERVATION_SENTINEL",""" +
      """"verdict":"gaps_found","produced_outputs":{"value":"{\"gaps\":[{\"criterion\":\"AC-001\",""" +
      """\"note\":\"the behavior is absent\",\"severity\":\"blocker\"}]}"}}"""

  fun correctedSatisfied(): String =
    """{"contract_version":"0.6","phase_id":"audit","status":"completed","summary":"criteria met",""" +
      """"verdict":"satisfied","produced_outputs":{"value":"$AUDIT_VALUE_SATISFIED"}}"""

  fun unsupportedBlockYaml(): String = """
      contract_version: "0.6"
      phase_id: "audit"
      status: "completed"
      summary: "$UNSUPPORTED_YAML_SENTINEL"
      verdict: "satisfied"
      produced_outputs:
        value: "$AUDIT_VALUE_SATISFIED"
  """.trimIndent()
}
