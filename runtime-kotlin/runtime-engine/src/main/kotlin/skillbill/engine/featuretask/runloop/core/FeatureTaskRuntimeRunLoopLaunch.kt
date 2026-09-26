package skillbill.engine.featuretask.runloop.core

import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseFileManifest
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.install.model.SupportedAgent
import skillbill.ports.workflow.gitops.model.WorkflowGitNameListResult
import skillbill.ports.workflow.gitops.model.WorkflowPathContentIdentitiesResult
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeCorrectiveRepairContext

object FeatureTaskRuntimeRunLoopLaunch {
  internal fun capturePhaseContentIdentities(
    request: FeatureTaskRuntimeRunRequest,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseGates: FeatureTaskRuntimePhaseGates,
    phaseId: String,
  ) {
    val owned = phaseGates.gitOperations.repositoryOwnedPaths(request.repoRoot)
    if (owned !is WorkflowGitNameListResult.Listed) return
    val paths = owned.names.map(String::trim).filter(String::isNotBlank)
    val identities = phaseGates.gitOperations.pathContentIdentities(request.repoRoot, paths)
    if (identities !is WorkflowPathContentIdentitiesResult.Resolved) return
    session.recordPhaseContentIdentities(phaseId, identities.identities)
  }

  internal fun outputEnvelopeOf(output: FeatureTaskRuntimePhaseOutput): Map<String, Any?>? =
    output.normalizedOutput?.envelopeWireMap()?.takeIf { it.isNotEmpty() }
      ?: JsonCodec.parseObjectOrNull(output.payload)?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap)

  internal fun launchedModelDirective(run: PhaseRun): LaunchedModelDirective {
    val model = run.modelDirective?.model
    val effort = run.modelDirective?.effort
    if (run.resolvedAgent.resolvedAgentId == SupportedAgent.CURSOR.id && model != null && effort != null) {
      return LaunchedModelDirective("$model[effort=$effort]", effort, persistedEffort = null)
    }
    return LaunchedModelDirective(model, effort, effort)
  }
}

internal sealed interface AttemptResult {
  data class Settled(val outcome: PhaseOutcome) : AttemptResult

  data class SchemaInvalid(
    val operatorReason: String,
    val retryReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
    override val rejectedOutput: String?,
    override val malformedOutput: Boolean,
    override val correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext?,
  ) : AttemptResult

  data class IncompleteWork(
    val operatorReason: String,
    val continuationReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
    val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  ) : AttemptResult

  data class RetryableTerminal(
    val operatorReason: String,
    val retryReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
    val failureDisposition: FeatureTaskRuntimeFailureDisposition,
  ) : AttemptResult

  data class BoundaryBodyDelivery(
    val continuationReason: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  data class FindingsOwed(
    val kind: FindingsOwedKind,
    val operatorReason: String,
    val retryReason: String,
    val refs: Set<String>,
    val detail: String?,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  data class AuditRetry(
    val focusHint: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  data class ValidationRemaining(
    val remainingFingerprint: String,
    val remainingDetail: String,
    override val fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ) : AttemptResult

  val settledOutcome: PhaseOutcome? get() = (this as? Settled)?.outcome
  val schemaInvalidOperatorReason: String? get() = (this as? SchemaInvalid)?.operatorReason
  val schemaInvalidRetryReason: String? get() = (this as? SchemaInvalid)?.retryReason
  val fileManifest: FeatureTaskRuntimePhaseFileManifest?
    get() =
      when (this) {
        is Settled -> null
        is SchemaInvalid -> fileManifest
        is IncompleteWork -> fileManifest
        is RetryableTerminal -> fileManifest
        is FindingsOwed -> fileManifest
        is BoundaryBodyDelivery -> fileManifest
        is AuditRetry -> fileManifest
        is ValidationRemaining -> fileManifest
      }
  val rejectedOutput: String? get() = (this as? SchemaInvalid)?.rejectedOutput
  val malformedOutput: Boolean get() = (this as? SchemaInvalid)?.malformedOutput == true
  val correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext?
    get() = (this as? SchemaInvalid)?.correctiveRepairContext

  val retryableOperatorReason: String?
    get() =
      when (this) {
        is Settled -> null
        is SchemaInvalid -> operatorReason
        is IncompleteWork -> operatorReason
        is RetryableTerminal -> operatorReason
        is FindingsOwed -> operatorReason
        is BoundaryBodyDelivery -> null
        is AuditRetry -> null
        is ValidationRemaining -> remainingDetail
      }

  val semanticRetryReason: String?
    get() =
      when (this) {
        is Settled -> null
        is SchemaInvalid -> retryReason
        is IncompleteWork -> null
        is RetryableTerminal -> null
        is FindingsOwed -> null
        is BoundaryBodyDelivery -> null
        is AuditRetry -> null
        is ValidationRemaining -> remainingDetail
      }

  val retryableTerminalRetryReason: String? get() = (this as? RetryableTerminal)?.retryReason

  val retryableTerminalDisposition: FeatureTaskRuntimeFailureDisposition?
    get() = (this as? RetryableTerminal)?.failureDisposition

  val findingsOwedKind: FindingsOwedKind? get() = (this as? FindingsOwed)?.kind

  val findingsOwedRefs: Set<String>? get() = (this as? FindingsOwed)?.refs

  val findingsOwedRetryReason: String? get() = (this as? FindingsOwed)?.retryReason

  val findingsOwedDetail: String? get() = (this as? FindingsOwed)?.detail

  val incompleteWorkContinuationReason: String? get() = (this as? IncompleteWork)?.continuationReason
  val incompleteWorkOutput: NormalizedFeatureTaskRuntimePhaseOutput?
    get() = (this as? IncompleteWork)?.normalizedOutput
  val boundaryBodyDeliveryContinuationReason: String?
    get() = (this as? BoundaryBodyDelivery)?.continuationReason

  val auditRetryFocusHint: String? get() = (this as? AuditRetry)?.focusHint

  val auditRetryContinuation: Boolean get() = this is AuditRetry

  val validationRemainingFingerprint: String? get() = (this as? ValidationRemaining)?.remainingFingerprint

  val validationRemainingDetail: String? get() = (this as? ValidationRemaining)?.remainingDetail

  companion object {
    fun settled(outcome: PhaseOutcome): AttemptResult = Settled(outcome)

    fun boundaryBodyDelivery(
      continuationReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult = BoundaryBodyDelivery(continuationReason, fileManifest)

    fun incompleteWork(
      operatorReason: String,
      continuationReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
      normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    ): AttemptResult = IncompleteWork(operatorReason, continuationReason, fileManifest, normalizedOutput)

    fun auditRetry(
      focusHint: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult = AuditRetry(focusHint, fileManifest)

    fun validationRemaining(
      remainingFingerprint: String,
      remainingDetail: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult = ValidationRemaining(remainingFingerprint, remainingDetail, fileManifest)

    fun unaccountedItems(
      phaseId: String,
      itemNoun: String,
      unaccountedRefs: List<String>,
      retryReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult =
      FindingsOwed(
        kind = FindingsOwedKind.OMITTED,
        operatorReason =
          "Phase '$phaseId' left carried $itemNoun unaccounted for in its output: " +
            unaccountedRefs.joinToString(", ") + ".",
        retryReason = retryReason,
        refs = unaccountedRefs.toSet(),
        detail = null,
        fileManifest = fileManifest,
      )

    fun unresolvedFindings(
      unresolvedRefs: Set<String>,
      detail: String,
      retryReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
    ): AttemptResult =
      FindingsOwed(
        kind = FindingsOwedKind.UNRESOLVED,
        operatorReason =
          "Phase 'implement_fix' reported carried review findings still open after " +
            "its attempt: ${unresolvedRefs.joinToString(", ")}.",
        retryReason = retryReason,
        refs = unresolvedRefs,
        detail = detail,
        fileManifest = fileManifest,
      )

    fun retryableTerminal(
      operatorReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
      failureDisposition: FeatureTaskRuntimeFailureDisposition,
    ): AttemptResult = RetryableTerminal(operatorReason, operatorReason, fileManifest, failureDisposition)

    fun schemaInvalid(
      operatorReason: String,
      fileManifest: FeatureTaskRuntimePhaseFileManifest,
      malformedOutput: Boolean = false,
      retryReason: String = operatorReason,
      correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext? = null,
    ): AttemptResult =
      SchemaInvalid(
        operatorReason = operatorReason,
        retryReason = retryReason,
        fileManifest = fileManifest,
        rejectedOutput = null,
        malformedOutput = malformedOutput,
        correctiveRepairContext = correctiveRepairContext,
      )
  }
}

internal sealed interface PhaseOutcome {
  data class Completed(val output: FeatureTaskRuntimePhaseOutput) : PhaseOutcome

  data class Blocked(val reason: String) : PhaseOutcome

  data class Paused(val reason: String) : PhaseOutcome

  data class RegenerateProducer(val producerPhaseId: String) : PhaseOutcome

  val completedOutput: FeatureTaskRuntimePhaseOutput? get() = (this as? Completed)?.output

  val blockedReason: String? get() = (this as? Blocked)?.reason

  val pausedReason: String? get() = (this as? Paused)?.reason

  val regenerationTargetPhaseId: String? get() = (this as? RegenerateProducer)?.producerPhaseId

  companion object {
    fun completed(output: FeatureTaskRuntimePhaseOutput): PhaseOutcome = Completed(output)

    fun blocked(reason: String): PhaseOutcome = Blocked(reason)

    fun paused(reason: String): PhaseOutcome = Paused(reason)

    fun regenerateProducer(producerPhaseId: String): PhaseOutcome = RegenerateProducer(producerPhaseId)
  }
}

internal sealed interface GoalReviewRunPreparation {
  data object CarryForward : GoalReviewRunPreparation

  class Blocked(
    val reason: String,
    val failureDisposition: FeatureTaskRuntimeFailureDisposition,
  ) : GoalReviewRunPreparation
}

internal data class GoalReviewRunReady(val run: PhaseRun) : GoalReviewRunPreparation

const val LEGACY_PLANNING_PROJECTION_LAUNCH_SEAM_REJECTION =
  "rejected an upstream bounded planning projection at the launch seam"
