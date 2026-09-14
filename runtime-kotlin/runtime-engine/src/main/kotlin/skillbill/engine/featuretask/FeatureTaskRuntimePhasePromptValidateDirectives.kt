package skillbill.engine.featuretask
import skillbill.engine.featuretask.validation.model.ValidationFindingSetProjection
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
private const val VALIDATE_PHASE_FORBIDDEN_EXTRAS: String =
  "Do not run `skill-bill validate`, `npx agnix`, `scripts/validate_agent_configs`, or any other " +
    "repo-root checklist. Those commands are not this phase. "

val RUNTIME_OWNED_VALIDATE_PHASE_TASK: String =
  runtimeOwnedValidateAgentPhaseTask(packGateDeclared = true, packConfirmationCommand = null)

private const val VALIDATE_AGENT_FORBIDDEN_PACK_GATE: String =
  "Do not run `bill-code-check`, the pack validation_gate collect_all_full_gate_command, " +
    "cache_bypassing_collect_all_full_gate_command, or any other pack-declared full-suite argv " +
    "during this validate turn — the runtime runs confirmation after you signal finished. "

const val VALIDATE_REPAIR_FIX_ALL_NO_MID_PROOF: String =
  "You may run narrowly scoped proof commands while working when a finding or local inspection names " +
    "a concrete task, script, or tool — never the pack's full collect-all or confirmation argv. " +
    "Do not substitute those full-suite commands for finishing; the runtime runs pack confirmation after you stop. "

private const val AGENT_RUN_VALIDATE_FIX_ALL_NO_MID_PROOF: String =
  "Before editing, copy the open findings into a numbered free-form checklist (file, rule, one-line " +
    "fix intent). Work through every checklist item — fix shared root causes once, not one full-suite " +
    "proof per item. Do not run the pack collect_all_full_gate_command, `bill-code-check`, or the pack " +
    "confirmation argv during this turn; the runtime re-runs the full gate after you stop. " +
    "After you have attempted a fix for every open finding, you may run any targeted proof command " +
    "relevant to those findings (the tool, script, or task the finding names); read-only inspection anytime. "

fun runtimeOwnedValidateAgentPhaseTask(
  packGateDeclared: Boolean,
  packConfirmationCommand: String?,
): String {
  val confirmationDetail = when {
    !packConfirmationCommand.isNullOrBlank() ->
      "The dominant platform pack declares validation_gate; after you signal finished the runtime alone " +
        "runs this confirmation argv: `$packConfirmationCommand` — you never run it."
    packGateDeclared ->
      "The dominant platform pack declares validation_gate; after you signal finished the runtime alone " +
        "runs validation_gate.cache_bypassing_collect_all_full_gate_command — you never run that confirmation argv."
    else ->
      "The runtime alone runs validation confirmation after you signal finished."
  }
  return "You are the only validate agent for this step — do not spawn delegated subagents. $confirmationDetail " +
    "This turn has no structured phase input or output: work against the repository and subtask context, " +
    "fix what you can using local inspection and targeted proofs only, then stop when your pass is done. " +
    "The finished signal does not claim pass or fail — the runtime decides by running pack confirmation. " +
    "The runtime may start up to three validate agent sessions; each retry is a fresh session with the same rules " +
    "and no injected finding list. " +
    VALIDATE_PHASE_FORBIDDEN_EXTRAS +
    VALIDATE_AGENT_FORBIDDEN_PACK_GATE +
    VALIDATE_REPAIR_FIX_ALL_NO_MID_PROOF +
    "Never silence findings with annotations, baselines, disabled rules, weakened configuration, or skipped " +
    "tests; fix root causes instead."
}

fun validateRepairPhaseTask(packConfirmationCommand: String?): String =
  runtimeOwnedValidateAgentPhaseTask(packGateDeclared = true, packConfirmationCommand = packConfirmationCommand)

fun validateGateTriagePhaseTask(): String =
  "You are triaging an unparseable validation gate failure blob before the first repair turn — do not spawn " +
    "delegated subagents. Read the gate stdout blob and repository files as needed to understand failures; " +
    "prefer read-only inspection. $VALIDATE_PHASE_FORBIDDEN_EXTRAS$VALIDATE_AGENT_FORBIDDEN_PACK_GATE" +
    "Do not mutate the tree unless strictly needed to understand failures. Emit a recommended " +
    "validation_repair_plan as prose inside produced_outputs.value (JSON string) with suggested fields per " +
    "item: item_id, module, rule_or_task, location, failure_summary, fix_intent. Extra keys are allowed. " +
    "Return prose guidance only; do not fix code or emit validation_result, gate_run_count, or gate evidence."

fun validatePhaseTask(
  packCollectAllCommand: String?,
  packGateDeclared: Boolean,
  packConfirmationCommand: String?,
): String = if (packGateDeclared) {
  runtimeOwnedValidateAgentPhaseTask(
    packGateDeclared = true,
    packConfirmationCommand = packConfirmationCommand,
  )
} else {
  agentRunValidateFallbackPhaseTask(packCollectAllCommand)
}

private fun agentRunValidateFallbackPhaseTask(packCollectAllCommand: String?): String {
  val collectAllLine = when {
    !packCollectAllCommand.isNullOrBlank() ->
      "Invoke bill-code-check for collect-all and confirmation. The dominant pack declares " +
        "validation_gate; its collect-all argv is `$packCollectAllCommand`. bill-code-check routes to " +
        "the pack quality-check skill, which must run exactly that argv for the initial collect-all " +
        "and for the one confirmation pass — do not rediscover a different full-suite command."
    else ->
      "Invoke bill-code-check for collect-all and confirmation. It auto-routes to the pack-declared " +
        "quality-check skill; never hard-code a stack-specific quality-check skill name."
  }
  return "You are the only validate agent for this step — do not spawn delegated subagents. $collectAllLine " +
    "Read that output, and fix every finding in this same session. " +
    VALIDATE_PHASE_FORBIDDEN_EXTRAS +
    "Do not rerun the full gate, bill-code-check, a cache-bypassing full check, or any targeted full-suite " +
    "proof after each individual finding. " + AGENT_RUN_VALIDATE_FIX_ALL_NO_MID_PROOF +
    "When the set looks clean, run bill-code-check " +
    "once to confirm (same pack collect-all). Findings that share one root cause are one fix, not several. " +
    "Validation findings are repair work, not a reason to block the phase. Fix findings at their root " +
    "cause; never silence them with annotations, baselines, disabled rules, weakened configuration, or " +
    "skipped tests. After you stop, the runtime re-runs the pack gate and mints the receipt — do not emit " +
    "validation_result, gate_run_count, or any phase-output JSON."
}

fun absentValidationGateDegradationDirective(phaseId: String, agentRunValidateFallback: Boolean): String {
  if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE || !agentRunValidateFallback) {
    return ""
  }
  return """
    ## Validation gate degradation
    The dominant platform pack declares no validation_gate. Validate falls back to agent-run
    bill-code-check routing only. This degradation is intentional and surfaced; do not treat
    absence of a runtime finding set as a clean pass. Agent-reported gate_run_count is never
    validation evidence.
  """.trimIndent()
}

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
      args.validationGateRepair -> validateRepairPhaseTask(args.packConfirmationGateCommand)
      else -> validatePhaseTask(
        packCollectAllCommand = args.packCollectAllCommand,
        packGateDeclared = !args.agentRunValidateFallback,
        packConfirmationCommand = args.packConfirmationGateCommand,
      )
    }
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT ->
      auditPhaseTaskDirective()
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT ->
      implementPhaseTaskDirective()
    else -> phaseDirectives[phaseId] ?: error("No phase directive for runtime phase '$phaseId'.")
  }

fun runtimeOwnedValidateFinishedDirective(
  phaseId: String,
  packConfirmationCommand: String?,
): String {
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
    validation_result, validation_receipt, gate_run_count, or any other phase envelope. Do not spawn
    delegated subagents. Work in ordinary prose; when your pass is done, stop — the runtime treats that as
    finished and immediately runs $confirmationLine (not you). Finished does not mean pass or fail.

    No structured input is injected (no finding list). Use the repository, subtask context, and your own
    inspection. You may run targeted local proofs while editing; do not run pack full-suite argv,
    bill-code-check, or any collect-all / confirmation command to substitute for finishing.
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
  "      Every audit invocation re-checks the complete listed criterion set from scratch against the\n" +
    "      current tree. Prior partial checks, provider sessions, and repair receipts do not skip checks.\n"
