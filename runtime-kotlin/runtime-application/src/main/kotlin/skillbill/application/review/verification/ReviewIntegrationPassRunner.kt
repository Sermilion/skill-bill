package skillbill.application.review.verification
import skillbill.application.review.model.ReviewIntegrationPassRunRequest
import skillbill.application.review.model.ReviewLaneIntegrationInput
import skillbill.application.review.packet.assignment
import skillbill.application.review.packet.commitUnits
import skillbill.application.review.packet.entries
import skillbill.application.review.packet.launch
import skillbill.application.review.packet.packet
import skillbill.application.review.packet.sha
import skillbill.application.review.packet.toIntegrationLaunchEnvelope
import skillbill.application.review.parallel.core.code.review.bundled.finding
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.end.finding
import skillbill.application.review.parallel.core.code.review.end.lanes
import skillbill.application.review.parallel.core.code.review.end.repoRoot
import skillbill.application.review.parallel.core.code.review.end.summary
import skillbill.application.review.parallel.core.code.review.evidence.coverage
import skillbill.application.review.parallel.core.code.review.evidence.entries
import skillbill.application.review.parallel.core.code.review.inline.completion
import skillbill.application.review.parallel.core.code.review.inline.failureReason
import skillbill.application.review.parallel.core.code.review.integration.finding
import skillbill.application.review.parallel.core.code.review.regression.coverage
import skillbill.application.review.parallel.core.code.review.regression.lanes
import skillbill.application.review.parallel.core.code.review.regression.repoRoot
import skillbill.application.review.parallel.core.code.review.regression.summary
import skillbill.application.review.parallel.core.code.review.runner.assignedPaths
import skillbill.application.review.parallel.core.code.review.runner.assignment
import skillbill.application.review.parallel.core.code.review.runner.budget
import skillbill.application.review.parallel.core.code.review.runner.completion
import skillbill.application.review.parallel.core.code.review.runner.coverage
import skillbill.application.review.parallel.core.code.review.runner.lanes
import skillbill.application.review.parallel.core.code.review.runner.launch
import skillbill.application.review.parallel.core.code.review.runner.modelOverride
import skillbill.application.review.parallel.core.code.review.runner.packet
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.runner.summary
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.review.assignment
import skillbill.application.review.parallel.core.review.completion
import skillbill.application.review.parallel.core.review.lanes
import skillbill.application.review.parallel.core.review.launch
import skillbill.application.review.parallel.core.review.packet
import skillbill.application.review.parallel.core.review.sha
import skillbill.application.review.parallel.core.review.source
import skillbill.application.review.parallel.core.review.unit
import skillbill.application.review.parallel.planning.budget
import skillbill.application.review.parallel.planning.digest
import skillbill.application.review.parallel.planning.input
import skillbill.application.review.parallel.planning.lane
import skillbill.application.review.parallel.planning.lanes
import skillbill.application.review.parallel.planning.repoRoot
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.parallel.verification.completion
import skillbill.application.review.parallel.verification.lane
import skillbill.application.review.parallel.verification.path
import skillbill.application.review.preparation.assignment
import skillbill.application.review.preparation.facts
import skillbill.application.review.preparation.launch
import skillbill.application.review.preparation.packet
import skillbill.application.review.preparation.request
import skillbill.application.review.preparation.unit
import skillbill.application.review.review.budget
import skillbill.application.review.review.commitSha
import skillbill.application.review.review.exitStatus
import skillbill.application.review.review.facts
import skillbill.application.review.review.interrupted
import skillbill.application.review.review.lane
import skillbill.application.review.review.lanes
import skillbill.application.review.review.repoRoot
import skillbill.application.review.review.spawnFailed
import skillbill.application.review.review.stdout
import skillbill.application.review.review.stdoutTruncated
import skillbill.application.review.review.timedOut
import skillbill.application.review.service.parse
import skillbill.application.review.service.review
import skillbill.application.review.service.validate
import skillbill.application.review.spec.brokerId
import skillbill.application.review.spec.budget
import skillbill.application.review.spec.digest
import skillbill.application.review.spec.facts
import skillbill.application.review.spec.finding
import skillbill.application.review.spec.issueKey
import skillbill.application.review.spec.lane
import skillbill.application.review.spec.lanes
import skillbill.application.review.spec.launch
import skillbill.application.review.spec.modelOverride
import skillbill.application.review.spec.packet
import skillbill.application.review.spec.path
import skillbill.application.review.spec.promptSuffix
import skillbill.application.review.spec.reason
import skillbill.application.review.spec.repoRoot
import skillbill.application.review.spec.stdout
import skillbill.application.review.spec.timeout
import skillbill.application.review.stats.digest
import skillbill.application.review.stats.lane
import skillbill.application.review.stats.summary
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.agentrun.model.UnsupportedAgentRunLaunch
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.ports.review.model.ReviewIntegrationPassOutcome
import skillbill.review.context.ReviewContextEnvelopeValidator
import skillbill.review.context.model.execution.ReviewSpecialistSummaryCoverage
import skillbill.review.context.model.execution.structuredString
import skillbill.review.context.model.launch.GovernedReviewIntegrationLaunch
import skillbill.review.context.model.launch.ReviewIntegrationTerminalOutcome
import skillbill.review.context.model.launch.ReviewSpecialistSummary
import skillbill.review.context.model.packet.ReviewContextPacket
import skillbill.review.context.model.packet.ReviewPacketConsumerContract
import skillbill.review.model.ParallelReviewRawFinding
import skillbill.review.model.ReviewFindingCitationDiagnosticWithFinding
import skillbill.review.parallel.ParallelReviewFindingParser

class ReviewIntegrationPassRunner(
  private val launcher: GoalRunnerSubtaskLauncher,
  private val envelopeValidator: ReviewContextEnvelopeValidator,
) {
  fun run(request: ReviewIntegrationPassRunRequest): ReviewIntegrationPassOutcome {
    skipReasonFor(request.packet, request.lanes)?.let { reason ->
      return ReviewIntegrationPassOutcome.skipped(request.packet.commitSequenceDigest, reason)
    }
    val integration = GovernedReviewIntegrationLaunch(
      packet = request.packet,
      specialistSummaries = request.lanes.map(::summaryOf),
      integrationContract = ReviewPacketConsumerContract.INTEGRATION_CONTRACT,
      brokerId = request.launch.brokerId,
      budget = request.launch.budget,
    )
    envelopeValidator.validate(
      integration.toIntegrationLaunchEnvelope().asWireMap(),
      "review integration launch for ${request.packet.reviewId}",
    )
    val prompt = appendPromptSuffix(integrationPrompt(integration), request.launch.promptSuffix)
    val outcome = launcher.launch(
      GoalRunnerSubtaskLaunchRequest(
        invokedAgentId = request.launch.brokerId,
        configuredAgentOverrideId = null,
        skillRunRequest = SkillRunRequest(
          issueKey = "code-review-integration",
          repoRoot = request.launch.repoRoot,
          timeout = request.launch.timeout,
          promptOverride = prompt,
          modelOverride = request.launch.modelOverride,
        ),
      ),
    )
    val launchBytes = prompt.toByteArray(Charsets.UTF_8).size.toLong()
    return when (outcome) {
      is UnsupportedAgentRunLaunch -> ReviewIntegrationPassOutcome(
        commitSequenceDigest = integration.commitSequenceDigest,
        terminalOutcome = ReviewIntegrationTerminalOutcome.UNSUPPORTED_PROVIDER,
        summarizedLaneCount = integration.specialistSummaries.size,
        launchBytes = launchBytes,
        failureReason = "unsupported agent: ${outcome.reason}",
      )
      is AgentRunLaunchFacts -> completedOutcome(integration, outcome, launchBytes)
    }
  }

  private fun completedOutcome(
    integration: GovernedReviewIntegrationLaunch,
    facts: AgentRunLaunchFacts,
    launchBytes: Long,
  ): ReviewIntegrationPassOutcome {
    val terminal = terminalOutcomeOf(facts)
    val parsed = if (terminal == ReviewIntegrationTerminalOutcome.COMPLETED) {
      crossCommitFindings(facts.stdout, integration)
    } else {
      CrossCommitFindings()
    }
    return ReviewIntegrationPassOutcome(
      commitSequenceDigest = integration.commitSequenceDigest,
      terminalOutcome = terminal,
      summarizedLaneCount = integration.specialistSummaries.size,
      findings = parsed.findings,
      citationDiagnostics = parsed.citationDiagnostics,
      launchBytes = launchBytes,
      resultBytes = facts.stdout.toByteArray(Charsets.UTF_8).size.toLong(),
      modelTurns = 1,
      failureReason = if (terminal == ReviewIntegrationTerminalOutcome.COMPLETED) null else terminal.wireValue,
    )
  }

  private fun crossCommitFindings(stdout: String, integration: GovernedReviewIntegrationLaunch): CrossCommitFindings {
    val owned = integration.packet.ownedCommitIds
    val parsed = ParallelReviewFindingParser.parse(stdout)
    val findings = parsed.findings.mapNotNull { finding ->
      val resolved = finding.commitShas.map { sha -> resolveCommitSha(sha, owned) }
      if (resolved.any { it == null }) return@mapNotNull null
      finding.copy(
        specialistSkillName = INTEGRATION_LANE,
        commitShas = resolved.filterNotNull().distinct(),
      )
    }.filter { it.commitShas.size > 1 }
    val sourceRefs = findings.mapNotNull { it.sourceFindingRef }.toSet()
    return CrossCommitFindings(
      findings = findings,
      citationDiagnostics = parsed.citationDiagnostics.filter { it.findingRef in sourceRefs },
    )
  }

  private fun resolveCommitSha(sha: String, owned: Set<String>): String? = when {
    sha in owned -> sha
    else -> owned.filter { it.startsWith(sha) }.singleOrNull()
  }

  private fun summaryOf(input: ReviewLaneIntegrationInput): ReviewSpecialistSummary {
    val decision = input.launch.assignment.laneDecision
    return ReviewSpecialistSummary.of(
      lane = input.launch.assignment.lane,
      assignmentDigest = input.launch.assignment.digest,
      completion = input.completion,
      coverage = ReviewSpecialistSummaryCoverage(
        assignedPaths = input.launch.assignment.assignedPaths,
        commitShas = input.launch.assignment.assignedBundle.entries.map { it.commitSha },
        findingCount = input.findingCount,
        summary = "Specialist '${decision.specialistSkillName}' reviewed " +
          "${input.launch.assignment.assignedPaths.size} assigned path(s) in one pass and reported " +
          "${input.findingCount} finding(s).",
      ),
    )
  }

  private fun skipReasonFor(packet: ReviewContextPacket, lanes: List<ReviewLaneIntegrationInput>): String? = when {
    packet.commitUnits.any { it.source.isSynthetic } ->
      "the review scope resolved to a synthetic unit, so there is no commit sequence to integrate over"
    packet.commitUnits.size < MIN_INTEGRATION_COMMITS ->
      "the commit sequence carries a single commit, so no cross-commit behavior exists to integrate"
    lanes.isEmpty() ->
      "no specialist lane reached a terminal state, so there are no lane summaries to integrate"
    else -> null
  }

  private fun integrationPrompt(integration: GovernedReviewIntegrationLaunch): String = buildString {
    appendLine(integration.integrationContract)
    appendLine()
    appendLine("Commit sequence (${integration.packet.commitUnits.size} commits, in order):")
    integration.packet.commitUnits.sortedBy { it.orderIndex }.forEach { unit ->
      appendLine("- ${unit.orderIndex}: ${unit.commitSha} ${structuredString(unit.subject.replace("\r\n", "\n"))}")
    }
    appendLine()
    appendLine("Specialist lane summaries (already reviewed; do not re-run their rubrics):")
    integration.specialistSummaries.sortedBy { it.lane }.forEach { summary ->
      appendLine(
        "- ${summary.lane} | coverage=${summary.disposition.wireValue} | " +
          "findings=${summary.findingCount} | ${summary.summary}",
      )
      if (!summary.isCleanCoverage) {
        appendLine(
          "  Coverage gap — this lane left unreviewed: ${summary.unreviewedUnits.joinToString(", ")}. " +
            "You are not reviewing these and must not report this gap as covered.",
        )
      }
    }
    appendLine()
    appendLine("Final-state evidence you may read (head revision):")
    integration.finalStateEvidenceTargets.forEach { target -> appendLine("- ${structuredString(target.path)}") }
    appendLine()
    appendLine(
      "Return only '[F-XXX] Severity | Confidence | commits=<sha>,<sha> | path=<JSON string> | " +
        "line=<positive integer> | description' lines. Every finding must name at least two commits " +
        "from the sequence above; a single-commit observation belongs to its specialist lane, not here.",
    )
  }

  private fun terminalOutcomeOf(facts: AgentRunLaunchFacts): ReviewIntegrationTerminalOutcome = when {
    facts.timedOut -> ReviewIntegrationTerminalOutcome.TIMEOUT
    facts.interrupted -> ReviewIntegrationTerminalOutcome.INTERRUPTED
    facts.spawnFailed -> ReviewIntegrationTerminalOutcome.SPAWN_FAILURE
    facts.stdoutTruncated -> ReviewIntegrationTerminalOutcome.PROCESS_FAILURE
    facts.exitStatus != 0 -> ReviewIntegrationTerminalOutcome.PROCESS_FAILURE
    else -> ReviewIntegrationTerminalOutcome.COMPLETED
  }

  private data class CrossCommitFindings(
    val findings: List<ParallelReviewRawFinding> = emptyList(),
    val citationDiagnostics: List<ReviewFindingCitationDiagnosticWithFinding> = emptyList(),
  )

  companion object {

    const val INTEGRATION_LANE: String = "review-integration"

    private const val MIN_INTEGRATION_COMMITS = 2
  }
}
