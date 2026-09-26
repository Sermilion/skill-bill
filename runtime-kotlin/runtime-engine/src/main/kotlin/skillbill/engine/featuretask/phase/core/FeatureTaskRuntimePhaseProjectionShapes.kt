package skillbill.engine.featuretask.phase.core

import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_BUILD_RECEIPT_CONTRACT_VERSION
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_REPAIR_RECEIPT_CONTRACT_VERSION
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

object FeatureTaskRuntimePhaseProjectionShapes {
  fun exampleFor(phaseId: String): String =
    when (phaseId) {
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN -> PREPLAN
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN -> PLAN
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT -> IMPLEMENT
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_SIMPLIFY -> SIMPLIFY
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX -> IMPLEMENT_FIX
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE -> VALIDATION
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD -> BUILD
      else -> ""
    }

  private fun prosePhaseOutputShape(
    innerJsonExample: String,
    trailingNotes: String,
  ): String =
    "\n    - Required produced_outputs shape: non-blank value string, optional prompt (omit the prompt " +
      "key when there is no directive; never set it to null). Put the JSON " +
      "object below INSIDE value as a JSON string; do not emit those fields as sibling keys on " +
      "produced_outputs. The runtime does not validate the inner shape; the next phase reads value " +
      "as structured prose and interprets it. Extra keys beside value are allowed.\n" +
      "      ```json\n" +
      "      { \"value\": \"<JSON string: inner object below>\", \"prompt\": \"<optional directive>\" }\n" +
      "      ```\n" +
      "      Inner object to stuff into value:\n" +
      "      ```json\n" +
      innerJsonExample +
      "      ```\n" +
      trailingNotes

  private val PREPLAN: String =
    prosePhaseOutputShape(
      innerJsonExample =
        "      { \"projection_kind\": \"preplanning_digest\",\n" +
          "        \"contract_version\": \"0.2\",\n" +
          "        \"affected_boundaries\": [\"<module or boundary touched>\"], \"patterns_and_decisions\": [],\n" +
          "        \"risks\": [\"<concrete risk>\"],\n" +
          "        \"rollout\": { \"flag_required\": false, \"flag_pattern\": \"none\",\n" +
          "          \"notes\": \"<rollout note, or N/A>\" },\n" +
          "        \"validation_strategy\": [\"<how the change is validated>\"],\n" +
          "        \"unresolved_questions\": [], \"evidence_refs\": [],\n" +
          "        \"selected_boundary_headings\": [\"<heading_id copied verbatim from the boundary catalog>\"] }\n",
      trailingNotes =
        "      flag_pattern is one of none, simple_conditional, di_switch, legacy. Walk boundary_memory " +
          "headings for relevance; weave context into the stuffed object rather than listing headings only " +
          "outside value.",
    )

  private val PLAN: String =
    prosePhaseOutputShape(
      innerJsonExample =
        "      { \"projection_kind\": \"executable_plan\",\n" +
          "        \"contract_version\": \"0.2\",\n" +
          "        \"mode\": \"direct\",\n" +
          "        \"tasks\": [ { \"task_id\": \"task-1\", \"depends_on\": [], " +
          "\"description\": \"<imperative task>\",\n" +
          "          \"criterion_refs\": [\"AC-001\"], \"target_paths_or_symbols\": [\"path/or/Symbol\"],\n" +
          "          \"test_obligations\": [\"<test to add or run>\"], \"constraints\": [] } ],\n" +
          "        \"validation_strategy\": [\"<how the plan is validated>\"] }\n",
      trailingNotes =
        "      Upstream preplan value is structured prose carrying the digest JSON; read and interpret it. " +
          "task_id MUST match ^[a-z][a-z0-9-]*\$ (lowercase kebab; \"T1\" is wrong — use \"task-1\"); " +
          "criterion_refs use the AC-### form.",
    )

  private val IMPLEMENT: String =
    prosePhaseOutputShape(
      innerJsonExample =
        "      { \"projection_kind\": \"implementation_receipt\",\n" +
          "        \"contract_version\": \"0.2\",\n" +
          "        \"completed_task_ids\": [\"task-1\"], \"changed_paths\": [\"path/Changed.kt\"],\n" +
          "        \"tests_added\": [], \"tests_updated\": [],\n" +
          "        \"tests_executed\": [],\n" +
          "        \"deviations\": [ { \"ref\": \"AC-001\", \"note\": \"<one-line what deviated and why>\" } ],\n" +
          "        \"unresolved_items\": [],\n" +
          "        \"reconciliation_evidence\": { \"reconciled\": true, \"evidence\": \"<tree at target>\" },\n" +
          "        \"reconciled_state\": { \"reconciled\": true, \"evidence\": \"<tree at target>\" } }\n",
      trailingNotes =
        "      Upstream plan value is structured prose carrying the executable_plan JSON; read and interpret it. " +
          "repository_checkpoint is runtime-owned: omit it entirely. Never invent a fingerprint. " +
          "Compilation and test execution belong exclusively to the validate phase; tests_executed stays []. " +
          "changed_paths are repository-relative; deviations entries are objects { \"ref\", \"note\" }.",
    )

  private val SIMPLIFY: String =
    prosePhaseOutputShape(
      innerJsonExample =
        "      { \"projection_kind\": \"simplification_receipt\",\n" +
          "        \"contract_version\": \"0.1\",\n" +
          "        \"changed_paths\": [\"path/Changed.kt\"],\n" +
          "        \"reductions\": [ { \"path\": \"path/Changed.kt\", \"outcome\": \"addressed\",\n" +
          "          \"note\": \"<one-line reduction>\" } ],\n" +
          "        \"unresolved_items\": [],\n" +
          "        \"reconciliation_evidence\": { \"reconciled\": true, \"evidence\": \"<tree at target>\" },\n" +
          "        \"reconciled_state\": { \"reconciled\": true, \"evidence\": \"<tree at target>\" } }\n",
      trailingNotes =
        "      Outcome on each reduction is no_edit, addressed, or unresolved. repository_checkpoint is " +
          "runtime-owned: omit it entirely. Never invent a fingerprint. Do not run builds or tests here.",
    )

  private val IMPLEMENT_FIX: String =
    "\n    - Required produced_outputs.repair_receipt shape: contract_version " +
      "\"$FEATURE_TASK_RUNTIME_REPAIR_RECEIPT_CONTRACT_VERSION\" and one entry per carried finding " +
      "with finding_id (aliases finding_ref, id, ref accepted) and outcome (addressed, " +
      "no_edit_required, or attempted_unresolved). Coverage matches on finding_id and outcome alone.\n" +
      "      Recommended optional fields per entry: constructs, intent, severity, label, text, " +
      "no_edit_reason, and unresolved_reason. The round number and pre-fix checkpoint sha are " +
      "runtime-owned: omit them, never guess them from a briefing hash:\n" +
      "      ```json\n" +
      "      { \"repair_receipt\": {\n" +
      "          \"contract_version\": \"$FEATURE_TASK_RUNTIME_REPAIR_RECEIPT_CONTRACT_VERSION\",\n" +
      "          \"entries\": [ { \"finding_id\": \"F-001\", \"outcome\": \"addressed\" } ] } }\n" +
      "      ```\n" +
      "      Compilation and test execution belong exclusively to the validate phase. Do NOT build,\n" +
      "      compile, run tests, or invoke `./gradlew check` / the pack collect-all gate here."

  private const val VALIDATION: String =
    "\n    - Settle completed only when every required project check passed, with a non-blank value of checks run.\n" +
      "      Example: { \"value\": \"<checks run and result>\" }\n" +
      "      When checks still fail, settle blocked with the remaining failures as the value and verdict\n" +
      "      progress when they shrank against the previous attempt, or no_progress when they did not.\n" +
      "      Do not emit validation_evidence, validation_result, gate_run_count, or gate_runs.\n" +
      "      Never introduce suppressions, baselines, disabled rules, or skipped tests to silence findings."

  private const val BUILD: String =
    "\n    - Required produced_outputs shape: emit a build_receipt OBJECT with contract_version\n" +
      "      \"$FEATURE_TASK_RUNTIME_BUILD_RECEIPT_CONTRACT_VERSION\":\n" +
      "      ```json\n" +
      "      { \"build_receipt\": {\n" +
      "          \"contract_version\": \"$FEATURE_TASK_RUNTIME_BUILD_RECEIPT_CONTRACT_VERSION\",\n" +
      "          \"validation_status\": \"passed\",\n" +
      "          \"checks\": [],\n" +
      "          \"repository_checkpoint\": { \"fingerprint\": \"<checkpoint fingerprint>\" },\n" +
      "          \"gate_run_count\": 1,\n" +
      "          \"gate_runs\": [ { \"duration_ms\": 1, \"outcome\": \"passed\",\n" +
      "            \"cache_mode\": \"forced_full\", \"executed_work_units\": 1 } ]\n" +
      "        } }\n" +
      "      ```\n" +
      "      Run only the pack build_command. Do not run collect_all_full_gate_command, check " + "--" + "continue,\n" +
      "      skill-bill validate, or bill-code-check. gate_run_count and gate_runs are runtime-measured."
}
