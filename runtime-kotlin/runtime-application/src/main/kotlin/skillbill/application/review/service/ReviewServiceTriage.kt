package skillbill.application.review.service
import skillbill.application.review.model.TriageResult
import skillbill.application.review.model.TriageResultKind
import skillbill.application.telemetry.settings.feedbackTelemetryOptions
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.review.repository.ReviewRepository
import skillbill.ports.telemetry.transport.TelemetrySettingsProvider
import skillbill.review.finding.TriageDecisionParser
import skillbill.review.model.FeedbackRequest
import skillbill.review.model.NumberedFinding
import skillbill.review.model.ReviewFinishedTelemetry
import skillbill.review.model.TriageDecision

internal data class TriageReviewRequest(
  val database: DatabaseSessionFactory,
  val settingsProvider: TelemetrySettingsProvider,
  val diagnostics: RuntimeDiagnostics,
  val runId: String,
  val decisions: List<String>,
  val listOnly: Boolean,
  val listWhenNoDecisions: Boolean,
  val routedSkillPlatformSlugs: Map<String, String>,
  val resolveRepoScopeKey: () -> String?,
)

internal fun triageReview(request: TriageReviewRequest): TriageResult =
  if (request.listOnly || (request.decisions.isEmpty() && request.listWhenNoDecisions)) {
    request.database.read { unitOfWork ->
      val numberedFindings = unitOfWork.reviews.fetchNumberedFindings(request.runId)
      TriageResult(
        kind = TriageResultKind.LIST,
        dbPath = unitOfWork.dbPath.toString(),
        reviewRunId = request.runId,
        findings = numberedFindings,
      )
    }
  } else {
    val recording =
      request.database.transaction { unitOfWork ->
        val numberedFindings = unitOfWork.reviews.fetchNumberedFindings(request.runId)
        RecordedTriage(
          dbPath = unitOfWork.dbPath.toString(),
          numberedFindings = numberedFindings,
          applied =
            applyTriageDecisions(
              TriageDecisionsRequest(
                settingsProvider = request.settingsProvider,
                diagnostics = request.diagnostics,
                reviewRepository = unitOfWork.reviews,
                runId = request.runId,
                numberedFindings = numberedFindings,
                decisions = request.decisions,
                routedSkillPlatformSlugs = request.routedSkillPlatformSlugs,
              ),
            ),
        )
      }
    TriageResult(
      kind = TriageResultKind.RECORDED,
      dbPath = recording.dbPath,
      reviewRunId = request.runId,
      recorded = recording.applied.recorded,
      learningCandidates =
        learningCandidates(
          reviewRunId = request.runId,
          recorded = recording.applied.recorded,
          numberedFindings = recording.numberedFindings,
          resolveRepoScopeKey = request.resolveRepoScopeKey,
        ),
      telemetry = recording.applied.telemetry,
    )
  }

private data class RecordedTriage(
  val dbPath: String,
  val numberedFindings: List<NumberedFinding>,
  val applied: AppliedTriageDecisions,
)

internal data class TriageDecisionsRequest(
  val settingsProvider: TelemetrySettingsProvider,
  val diagnostics: RuntimeDiagnostics,
  val reviewRepository: ReviewRepository,
  val runId: String,
  val numberedFindings: List<NumberedFinding>,
  val decisions: List<String>,
  val routedSkillPlatformSlugs: Map<String, String>,
)

internal fun applyTriageDecisions(request: TriageDecisionsRequest): AppliedTriageDecisions {
  val parsedDecisions = TriageDecisionParser.parseTriageDecisions(request.decisions, request.numberedFindings)
  var telemetry: ReviewFinishedTelemetry? = null
  parsedDecisions.forEach { decision ->
    val returnedTelemetry =
      request.reviewRepository.recordFeedback(
        FeedbackRequest(request.runId, listOf(decision.findingId), decision.outcomeType, decision.note),
        feedbackTelemetryOptions(request.settingsProvider, request.diagnostics),
        routedSkillPlatformSlugs = request.routedSkillPlatformSlugs,
      )
    if (returnedTelemetry != null) {
      telemetry = returnedTelemetry
    }
  }
  return AppliedTriageDecisions(
    recorded =
      parsedDecisions.map { decision ->
        TriageDecision(
          number = decision.number,
          findingId = decision.findingId,
          outcomeType = decision.outcomeType,
          note = decision.note,
        )
      },
    telemetry = telemetry,
  )
}

internal data class AppliedTriageDecisions(
  val recorded: List<TriageDecision>,
  val telemetry: ReviewFinishedTelemetry?,
)
