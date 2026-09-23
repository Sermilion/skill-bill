package skillbill.cli.kernel.cli
import skillbill.application.learning.model.LearningListResult
import skillbill.application.learning.model.LearningResolveResult
import skillbill.application.review.model.TriageResult
import skillbill.contracts.learning.summarizeAppliedLearnings
import skillbill.learnings.model.LearningEntry

internal data class CliLearningListPresentation(
  val entries: List<CliLearningLine>,
)

internal data class CliLearningLine(
  val reference: String,
  val status: String,
  val scopeLabel: String,
  val title: String,
)

internal data class CliResolvedLearningsPresentation(
  val scopePrecedence: String,
  val repoScopeKey: String?,
  val skillName: String?,
  val appliedLearnings: String,
  val entries: List<CliResolvedLearningLine>,
)

internal data class CliResolvedLearningLine(
  val reference: String,
  val scopeLabel: String,
  val title: String,
  val ruleText: String,
)

internal data class CliNumberedFindingsPresentation(
  val reviewRunId: String,
  val findings: List<CliNumberedFindingLine>,
)

internal data class CliNumberedFindingLine(
  val number: Int,
  val findingId: String,
  val severity: String,
  val confidence: String,
  val location: String,
  val description: String,
  val claimVerdict: String? = null,
  val scopeDisposition: String? = null,
  val severityAdjustment: String? = null,
)

internal data class CliTriagePresentation(
  val reviewRunId: String,
  val decisions: List<CliTriageDecisionLine>,
)

internal data class CliTriageDecisionLine(
  val number: Int,
  val findingId: String,
  val outcomeType: String,
  val note: String,
)

internal fun LearningListResult.toCliPresentation(): CliLearningListPresentation =
  CliLearningListPresentation(entries = learnings.map(LearningEntry::toCliLearningLine))

internal fun LearningResolveResult.toCliPresentation(): CliResolvedLearningsPresentation =
  CliResolvedLearningsPresentation(
    scopePrecedence = scopePrecedence.joinToString(" > ") { scope -> scope.wireName },
    repoScopeKey = repoScopeKey,
    skillName = skillName,
    appliedLearnings = summarizeAppliedLearnings(learnings.map(LearningEntry::reference)),
    entries = learnings.map(LearningEntry::toCliResolvedLearningLine),
  )

internal fun TriageResult.toCliNumberedFindingsPresentation(reviewRunId: String): CliNumberedFindingsPresentation =
  CliNumberedFindingsPresentation(
    reviewRunId = reviewRunId,
    findings =
      findings.map { finding ->
        CliNumberedFindingLine(
          number = finding.number,
          findingId = finding.findingId,
          severity = finding.severity,
          confidence = finding.confidence,
          location = finding.location,
          description = finding.description,
          claimVerdict = finding.claimVerdict?.wireValue,
          scopeDisposition = finding.scopeDisposition?.wireValue,
          severityAdjustment =
            finding.severityAdjustment?.let { adjustment ->
              "${adjustment.direction.wireValue}: ${adjustment.justification}"
            },
        )
      },
  )

internal fun TriageResult.toCliTriagePresentation(reviewRunId: String): CliTriagePresentation =
  CliTriagePresentation(
    reviewRunId = reviewRunId,
    decisions =
      recorded.map { decision ->
        CliTriageDecisionLine(
          number = decision.number,
          findingId = decision.findingId,
          outcomeType = decision.outcomeType,
          note = decision.note,
        )
      },
  )

private fun LearningEntry.toCliLearningLine(): CliLearningLine =
  CliLearningLine(
    reference = reference,
    status = status,
    scopeLabel = scopeLabel,
    title = title,
  )

private fun LearningEntry.toCliResolvedLearningLine(): CliResolvedLearningLine =
  CliResolvedLearningLine(
    reference = reference,
    scopeLabel = scopeLabel,
    title = title,
    ruleText = ruleText,
  )
