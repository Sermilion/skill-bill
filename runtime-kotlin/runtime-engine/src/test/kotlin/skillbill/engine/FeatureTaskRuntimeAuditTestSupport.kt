package skillbill.engine

internal const val AUDIT_GAP_MESSAGE = "AC-002 acceptance criterion is not yet implemented"

internal fun auditSatisfiedOutput(): String = """
  {
    "contract_version": "0.6",
    "phase_id": "audit",
    "status": "completed",
    "summary": "Every acceptance criterion is met.",
    "verdict": "satisfied",
    "produced_outputs": {
      "value": "All planned acceptance criteria have implementation and meaningful test coverage."
    }
  }
""".trimIndent()

internal fun auditGapsFoundOutput(): String = """
  {
    "contract_version": "0.6",
    "phase_id": "audit",
    "status": "completed",
    "summary": "Audit found unmet acceptance criteria.",
    "verdict": "gaps_found",
    "produced_outputs": {
      "value": "{\"gaps\":[{\"criterion\":\"AC-002\",\"note\":\"$AUDIT_GAP_MESSAGE\"}],\"non_blocking_findings\":[]}"
    }
  }
""".trimIndent()

internal fun auditBlockedOutput(reason: String): String = """
  {
    "contract_version": "0.6",
    "phase_id": "audit",
    "status": "blocked",
    "failure_disposition": "needs_user_action",
    "summary": "$reason",
    "produced_outputs": {
      "value": "$reason"
    }
  }
""".trimIndent()

internal fun satisfiedAuditLauncher(): RuntimeRecordingLauncher = RuntimeRecordingLauncher { request ->
  val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
  if (phaseId == "audit") {
    facts(auditSatisfiedOutput())
  } else {
    facts(defaultPhaseOutput(request))
  }
}
