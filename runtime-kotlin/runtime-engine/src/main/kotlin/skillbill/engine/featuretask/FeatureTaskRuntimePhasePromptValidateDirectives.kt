package skillbill.engine.featuretask
import skillbill.engine.featuretask.validation.model.ValidationFindingSetProjection
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
private const val VALIDATE_PHASE_FORBIDDEN_EXTRAS: String =
  "Do not run `skill-bill validate`, `npx agnix`, `scripts/validate_agent_configs`, or any other " +
    "repo-root checklist. Those commands are not this phase. "

val RUNTIME_OWNED_VALIDATE_PHASE_TASK: String =
  runtimeOwnedValidateAgentPhaseTask(packCollectAllCommand = null, packConfirmationCommand = null)

private const val VALIDATE_AGENT_FORBIDDEN_CONFIRMATION: String =
  "Do not run `bill-code-check` or validation_gate.cache_bypassing_collect_all_full_gate_command — " +
    "the runtime runs confirmation after you stop. "

private const val VALIDATE_TRIAGE_FORBIDDEN_PACK_GATE: String =
  "Do not run `bill-code-check`, the pack validation_gate collect_all_full_gate_command, " +
    "cache_bypassing_collect_all_full_gate_command, or any other pack-declared full-suite argv " +
    "during this triage turn. "

const val VALIDATE_REPAIR_FIX_ALL_NO_MID_PROOF: String =
  "Run the pack collect-all once at the start of this turn, read that output, and fix every finding " +
    "in this session. Do not rerun collect-all after each item. You may run narrowly scoped proof " +
    "commands while working when a finding or local inspection names a concrete task, script, or tool. " +
    "Do not run the confirmation argv; the runtime runs pack confirmation after you stop. "

fun runtimeOwnedValidateAgentPhaseTask(packCollectAllCommand: String?, packConfirmationCommand: String?): String {
  val collectAllDetail = when {
    !packCollectAllCommand.isNullOrBlank() ->
      "Run this collect-all argv once: `$packCollectAllCommand`."
    else ->
      "Run validation_gate.collect_all_full_gate_command from the dominant platform pack once."
  }
  val confirmationDetail = when {
    !packConfirmationCommand.isNullOrBlank() ->
      "After you stop, the runtime alone runs this confirmation argv: `$packConfirmationCommand` — you never run it."
    else ->
      "After you stop, the runtime alone runs validation_gate.cache_bypassing_collect_all_full_gate_command — " +
        "you never run that confirmation argv."
  }
  return "You are the only validate agent for this step — do not spawn delegated subagents. $collectAllDetail " +
    "Read that output and fix every finding in this same session. When the pass is done, stop. " +
    "Do not emit phase-output JSON, validation_result, or validation_evidence. $confirmationDetail " +
    "If confirmation is still red, the runtime starts a fresh validate session with the same rules " +
    "(up to three validate agent sessions, no injected finding list). Finished does not claim pass or fail. " +
    VALIDATE_PHASE_FORBIDDEN_EXTRAS +
    VALIDATE_AGENT_FORBIDDEN_CONFIRMATION +
    VALIDATE_REPAIR_FIX_ALL_NO_MID_PROOF +
    "Never silence findings with annotations, baselines, disabled rules, weakened configuration, or skipped " +
    "tests; fix root causes instead."
}

fun validateGateTriagePhaseTask(): String =
  "You are triaging an unparseable validation gate failure blob before the first repair turn — do not spawn " +
    "delegated subagents. Read the gate stdout blob and repository files as needed to understand failures; " +
    "prefer read-only inspection. $VALIDATE_PHASE_FORBIDDEN_EXTRAS$VALIDATE_TRIAGE_FORBIDDEN_PACK_GATE" +
    "Do not mutate the tree unless strictly needed to understand failures. Emit a recommended " +
    "validation_repair_plan as prose inside produced_outputs.value (JSON string) with suggested fields per " +
    "item: item_id, module, rule_or_task, location, failure_summary, fix_intent. Extra keys are allowed. " +
    "Return prose guidance only; do not fix code or emit validation_result, gate_run_count, or gate evidence."

internal data class PhaseTaskDirectiveArgs(
  val agentRunValidateFallback: Boolean = false,
  val packCollectAllCommand: String? = null,
  val packConfirmationGateCommand: String? = null,
  val packBuildCommand: String? = null,
  val validationGateRepair: Boolean = false,
  val validationGateTriage: Boolean = false,
  val acceptanceCriteria: List<String> = emptyList(),
)

internal fun phaseTaskDirective(phaseId: String, args: PhaseTaskDirectiveArgs = PhaseTaskDirectiveArgs()): String =
  when (phaseId) {
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD -> when {
      args.validationGateTriage -> buildGateTriagePhaseTask(args.packBuildCommand)
      else -> runtimeOwnedBuildPhaseTask(args.packBuildCommand)
    }
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE -> when {
      args.validationGateTriage -> validateGateTriagePhaseTask()
      else -> runtimeOwnedValidateAgentPhaseTask(
        packCollectAllCommand = args.packCollectAllCommand,
        packConfirmationCommand = args.packConfirmationGateCommand,
      )
    }
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT ->
      auditPhaseTaskDirective()
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT ->
      implementPhaseTaskDirective()
    else -> phaseDirectives[phaseId] ?: error("No phase directive for runtime phase '$phaseId'.")
  }

fun runtimeOwnedValidateFinishedDirective(phaseId: String, packConfirmationCommand: String?): String {
  if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE) {
    return gateRepairNoOutputSchemaDirective(phaseId, triage = false)
  }
  val confirmationLine = when {
    !packConfirmationCommand.isNullOrBlank() ->
      "the pack confirmation argv `$packConfirmationCommand`"
    else ->
      "validation_gate.cache_bypassing_collect_all_full_gate_command from the dominant platform pack"
  }
  return """
    ## Validate — finished signal only
    This launch is a runtime-owned validate agent turn. Do not emit a Required final output JSON object,
    validation_result, validation_evidence, validation_receipt, gate_run_count, or any other phase envelope.
    Do not spawn delegated subagents. Run the pack collect-all once, fix every finding, then stop — the
    runtime treats that as finished and immediately runs $confirmationLine (not you). If confirmation is
    still red, the runtime starts a fresh validate session. Finished does not mean pass or fail.

    No structured input is injected (no finding list). Use the repository, subtask context, and the
    collect-all output. You may run targeted local proofs while editing; do not run bill-code-check or
    the confirmation argv to substitute for finishing.
  """.trimIndent()
}

fun gateRepairNoOutputSchemaDirective(phaseId: String, triage: Boolean = false): String {
  if (triage) {
    return """
      ## Gate triage — optional capture surface, no phase-output schema
      This launch triages an unparseable gate blob before the first repair turn for the runtime-owned `$phaseId` gate.
      Do not emit a Required final output JSON object, build_receipt, validation_receipt, gate_run_count, or any other
      phase receipt or gate evidence. Do not spawn delegated subagents. Read the blob and cited paths.
      When you can recommend a repair shape, you may emit produced_outputs.value (a JSON string) carrying
      validation_repair_plan prose with suggested fields per item: item_id, module, rule_or_task, location,
      failure_summary, fix_intent. Malformed or missing capture is fine; repair still runs without it.
    """.trimIndent()
  }
  return """
  ## Gate repair — prose only, no phase-output schema
  This launch is a repair turn for the runtime-owned `$phaseId` gate. Do not emit a Required final
  output JSON object, build_receipt, validation_receipt, gate_run_count, or any other phase envelope.
  Do not spawn delegated subagents. Work in this single agent session in ordinary prose.

  The runtime already ran the pack command and parsed the failures listed in this briefing. It will
  re-run that command after you stop, and it may give you up to three repair turns against whatever
  remains. Address every open finding in this turn — all at once, not one finding per turn.

  Before editing, do brief reasoned planning in prose for each finding (or for a shared root cause
  that covers several). Scale the plan to the finding:
  - Small / obvious: a few lines of due diligence, then fix.
  - Complex: a real short plan — blast radius, surrounding callers/contracts you checked, whether
    the change can introduce new bugs, and how you will keep the fix local.

  No defined plan schema. Do the thinking, then edit. After you have attempted a fix for every open
  finding, you may run targeted proof commands relevant to those findings (the tool or task named in
  the finding). Stop when done; the runtime re-runs the pack gate.
  Never silence findings with @Suppress, @file:Suppress, baselines, disabled rules, weakened
  configuration, or skipped tests — fix the root cause instead.
  """.trimIndent()
}

fun validationGateFindingsDirective(
  phaseId: String,
  findings: ValidationFindingSetProjection?,
  triagePlan: String?,
): String {
  if (findings == null) return ""
  val (sectionTitle, preamble) = when (phaseId) {
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE -> return ""
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD -> Pair(
      "## Runtime build gate findings",
      "A prior gate run parsed these items. They are the full open set for this repair turn — fix " +
        "every one in this session (shared root causes may collapse several into one change). Run only " +
        "the pack-declared build command when you need console detail. Do not run `skill-bill " +
        "validate`, `bill-code-check`, or the pack collect_all_full_gate_command. Do not spawn delegated subagents.",
    )
    else -> return ""
  }
  val lines = buildList {
    add(sectionTitle)
    add(preamble)
    findings.findings.forEachIndexed { index, finding ->
      add(
        "${index + 1}. module=${finding.module} id=${finding.ruleOrTestId} " +
          "location=${finding.location ?: "<unknown>"} message=${finding.message}",
      )
    }
    if (!triagePlan.isNullOrBlank()) {
      add("## Triage working notes")
      add(triagePlan)
    }
  }
  return lines.joinToString("\n")
}

fun auditNoEarlierAuditLine(): String =
  "      Every audit invocation re-checks the complete in-scope criterion set from scratch against the\n" +
    "      current tree. Prior partial checks, provider sessions, and repair receipts do not skip checks.\n"
