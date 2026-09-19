package skillbill.application.workflow.persist
import skillbill.application.workflow.decomposition.artifacts
import skillbill.application.workflow.decomposition.artifactsJson
import skillbill.application.workflow.decomposition.clock
import skillbill.application.workflow.decomposition.effectiveInput
import skillbill.application.workflow.decomposition.error
import skillbill.application.workflow.decomposition.existing
import skillbill.application.workflow.decomposition.gitOperations
import skillbill.application.workflow.decomposition.issueKey
import skillbill.application.workflow.decomposition.map
import skillbill.application.workflow.decomposition.repoRoot
import skillbill.application.workflow.decomposition.steps
import skillbill.application.workflow.decomposition.updated
import skillbill.application.workflow.decomposition.validator
import skillbill.application.workflow.decomposition.workflowStatus
import skillbill.application.workflow.model.GoalObservabilityProgressInput
import skillbill.application.workflow.model.GoalObservabilityWorktreeActivity
import skillbill.application.workflow.model.PersistOpenedWorkflowArgs
import skillbill.application.workflow.model.WorkflowFamilyKind
import skillbill.application.workflow.model.WorkflowOpenResult
import skillbill.application.workflow.model.WorkflowServiceOpenArgs
import skillbill.application.workflow.model.WorkflowServiceOpenFeatureTaskArgs
import skillbill.application.workflow.model.WorkflowUpdateRequest
import skillbill.application.workflow.model.WorkflowUpdateResult
import skillbill.application.workflow.service.FEATURE_TASK_FAMILY_KINDS
import skillbill.application.workflow.service.INCOMPLETE_FEATURE_TASK_IDENTITY_ERROR
import skillbill.application.workflow.service.SUFFIX_CHARS
import skillbill.application.workflow.service.WORKFLOW_ID_SUFFIX_LENGTH
import skillbill.application.workflow.service.WorkflowService
import skillbill.application.workflow.service.artifacts
import skillbill.application.workflow.service.artifactsJson
import skillbill.application.workflow.service.clock
import skillbill.application.workflow.service.database
import skillbill.application.workflow.service.effectiveInput
import skillbill.application.workflow.service.effectiveSessionId
import skillbill.application.workflow.service.error
import skillbill.application.workflow.service.executionIdentity
import skillbill.application.workflow.service.existing
import skillbill.application.workflow.service.get
import skillbill.application.workflow.service.gitOperations
import skillbill.application.workflow.service.input
import skillbill.application.workflow.service.open
import skillbill.application.workflow.service.unitOfWork
import skillbill.application.workflow.service.updated
import skillbill.application.workflow.workflow.definition
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.issuekey.normalizeIssueKey
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.GoalObservabilityArtifacts
import skillbill.ports.workflow.get
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationStatus
import skillbill.ports.workflow.saveRecord
import skillbill.ports.workflow.toRecord
import skillbill.workflow.engine.RUNTIME_REPOSITORY_EVIDENCE_ARTIFACT_KEY
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowContinueDecision
import skillbill.workflow.engine.model.WorkflowDefinition
import skillbill.workflow.engine.model.WorkflowSnapshotView
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.goal.GoalObservabilityEventValidator
import skillbill.workflow.model.WorkflowStatus
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset
import kotlin.random.Random

fun incompleteFeatureTaskIdentityError(args: WorkflowServiceOpenArgs): WorkflowOpenResult.Error? {
  val hasIdentityCoordinates = args.repositoryIdentity != null || args.governedSpecPath != null
  val hasIncompleteIdentity = hasIncompleteFeatureTaskIdentity(
    args.kind,
    hasIdentityCoordinates,
    args.issueKey,
    args.repositoryIdentity,
    args.governedSpecPath,
  )
  return if (hasIncompleteIdentity) {
    WorkflowOpenResult.Error(
      workflowId = "unassigned",
      error = INCOMPLETE_FEATURE_TASK_IDENTITY_ERROR,
    )
  } else {
    null
  }
}

fun persistOpenedWorkflow(args: PersistOpenedWorkflowArgs): WorkflowOpenResult =
  args.database.transaction { unitOfWork ->
    val engine = args.engine
    val family = args.family
    val workflowId = args.workflowId
    val stepId = args.stepId
    val record = engine.openRecord(
      family.definition,
      workflowId,
      args.effectiveSessionId,
      stepId,
    )
    family.saveRecord(
      unitOfWork.workflowStates,
      record.toRecord().copy(
        startedAt = null,
        issueKey = normalizeIssueKey(args.issueKey),
      ),
    )
    args.executionIdentity?.let(unitOfWork.workflowStates::saveFeatureTaskExecutionIdentity)
    val saved = family.get(unitOfWork.workflowStates, workflowId) ?: record
    val currentStep = engine.snapshotView(family.definition, saved).steps
      .firstOrNull { it.stepId == stepId }
    val launchProjection = launchProjectionIfReady(
      engine,
      family.definition,
      engine.snapshotView(family.definition, saved),
      stepId,
      currentStep?.attemptCount ?: 0,
    )
    WorkflowOpenResult.Ok(
      workflowId = saved.workflowId,
      dbPath = unitOfWork.dbPath.toString(),
      snapshot = engine.snapshotView(family.definition, saved),
      launchProjection = launchProjection,
    )
  }

val resolveEffectiveSessionId =
  { kind: WorkflowFamilyKind, sessionId: String, definition: WorkflowDefinition, workflowId: String ->
    sessionId.ifBlank {
      if (kind == WorkflowFamilyKind.TASK_RUNTIME) "${definition.defaultSessionPrefix}-$workflowId" else ""
    }
  }

fun WorkflowUpdateRequest.toWorkflowUpdateInput(): WorkflowUpdateInput = WorkflowUpdateInput(
  workflowStatus = WorkflowStatus.fromWire(workflowStatus)
    ?: throw InvalidWorkflowStateSchemaError(
      "Invalid workflow_status '$workflowStatus'.",
    ),
  currentStepId = currentStepId,
  stepUpdates = stepUpdates,
  artifactsPatch = artifactsPatch,
  sessionId = sessionId,
)

fun WorkflowContinueDecision.toReopenInput(sessionId: String): WorkflowUpdateInput = WorkflowUpdateInput(
  workflowStatus = WorkflowStatus.RUNNING,
  currentStepId = resumeStepId,
  stepUpdates = WorkflowStepUpdates.from(
    listOf(
      mapOf(
        SharedPayloadKeys.STEP_ID to resumeStepId,
        SharedPayloadKeys.STATUS to "running",
        "attempt_count" to nextAttemptCount,
      ),
    ),
  ),
  artifactsPatch = null,
  sessionId = sessionId,
)

fun WorkflowUpdateInput.withGoalObservabilityArtifacts(
  existing: WorkflowStateSnapshot,
  workflowId: String,
  validator: GoalObservabilityEventValidator,
  gitOperations: WorkflowGitOperations,
  repoRoot: Path,
): WorkflowUpdateInput {
  val patch = artifactsPatch
  return if (patch?.containsKey("progress_event") != true) {
    this
  } else {
    val existingArtifacts = JsonCodec.parseObjectOrNull(existing.artifactsJson)
      ?.let(JsonCodec::jsonElementToValue)
      ?.let(JsonCodec::anyToStringAnyMap)
      .orEmpty()
    val mergedArtifacts = LinkedHashMap(existingArtifacts).apply { putAll(patch) }
    val observabilityPatch = GoalObservabilityArtifacts.patchForProgressEvent(
      input = GoalObservabilityProgressInput(
        artifacts = mergedArtifacts,
        workflowId = workflowId,
        workflowStatus = workflowStatus.wireValue,
        currentStepId = currentStepId,
        worktreeActivity = gitOperations.worktreeActivity(repoRoot.normalize())
          .takeIf { activity -> activity.status == WorkflowGitOperationStatus.OK }
          ?.let { activity ->
            GoalObservabilityWorktreeActivity(
              changedFileSummary = activity.changedFileSummary,
              diffStat = activity.diffStat,
            )
          },
      ),
      validator = validator,
    )
    observabilityPatch?.let { patchValue ->
      val decoded = JsonCodec.anyToStringAnyMap(patchValue) ?: return this
      copy(artifactsPatch = WorkflowArtifactPatch.from(LinkedHashMap(patch).apply { putAll(decoded) }))
    } ?: this
  }
}

fun buildUpdateOk(
  engine: WorkflowEngine,
  definition: WorkflowDefinition,
  updated: WorkflowStateSnapshot,
  effectiveInput: WorkflowUpdateInput,
  dbPath: String,
): WorkflowUpdateResult.Ok {
  val snapshot = engine.snapshotView(definition, updated)
  val currentStep = snapshot.steps.firstOrNull { it.stepId == snapshot.currentStepId }
  return WorkflowUpdateResult.Ok(
    workflowId = updated.workflowId,
    dbPath = dbPath,
    acknowledgement = engine.updateAcknowledgementView(
      snapshot = snapshot,
      input = effectiveInput,
    ),
    launchProjection = launchProjectionIfReady(
      engine,
      definition,
      snapshot,
      snapshot.currentStepId,
      currentStep?.attemptCount ?: 0,
    ),
  )
}

fun launchProjectionIfReady(
  engine: WorkflowEngine,
  definition: WorkflowDefinition,
  snapshot: WorkflowSnapshotView,
  stepId: String,
  producerIteration: Int,
) = definition.inputProjectionsByStep[stepId]
  ?.takeIf { declaration ->
    declaration.requiredArtifactKeys.all { artifactKey ->
      artifactKey == RUNTIME_REPOSITORY_EVIDENCE_ARTIFACT_KEY || snapshot.artifacts.containsKey(artifactKey)
    }
  }
  ?.let { engine.launchProjection(definition, snapshot, stepId, producerIteration) }

fun WorkflowService.openFeatureTask(args: WorkflowServiceOpenFeatureTaskArgs): WorkflowOpenResult {
  require(args.kind in FEATURE_TASK_FAMILY_KINDS) {
    "Only runtime feature-task workflows use execution identity."
  }
  return open(
    WorkflowServiceOpenArgs(
      kind = args.kind,
      sessionId = args.sessionId,
      currentStepId = args.currentStepId,
      issueKey = args.issueKey,
      repositoryIdentity = args.repositoryIdentity,
      governedSpecPath = args.governedSpecPath,
      routeScope = args.routeScope,
    ),
  )
}

fun generateWorkflowId(prefix: String, clock: Clock, random: Random): String {
  val now = clock.instant().atOffset(ZoneOffset.UTC)
  val suffix = (1..WORKFLOW_ID_SUFFIX_LENGTH).map { SUFFIX_CHARS[random.nextInt(SUFFIX_CHARS.length)] }
    .joinToString("")
  return "$prefix-${now.year}${now.monthValue.twoDigits()}${now.dayOfMonth.twoDigits()}-" +
    "${now.hour.twoDigits()}${now.minute.twoDigits()}${now.second.twoDigits()}-$suffix"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
