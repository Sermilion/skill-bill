package skillbill.application.workflow

import skillbill.application.decomposition.DECOMPOSITION_RUNTIME_ARTIFACT_KEY
import skillbill.workflow.decomposition.encodeManifestWireMap
import skillbill.application.decomposition.withRetriedSubtask
import skillbill.contracts.JsonCodec
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.workflow.get
import skillbill.ports.workflow.saveRecord
import skillbill.ports.workflow.toRecord
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.decodeGoalContinuationArtifactFromArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationArtifact

fun WorkflowEngine.updateGoalParentForBlockedPhaseRetry(
  unitOfWork: GoalRunnerPersistenceSession,
  childWorkflowId: String,
  childArtifacts: DurableWorkflowArtifacts,
  phaseId: String,
  validator: DecompositionManifestValidator,
): String? {
  val rawContinuation = childArtifacts[FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY]
    ?: return null
  val continuationMap = JsonCodec.anyToStringAnyMap(rawContinuation)
    ?: invalidGoalRetryProjection(
      "Workflow artifact '$FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY' must be an object.",
    )
  val continuation = decodeGoalContinuationArtifactFromArtifact(continuationMap)
    ?: invalidGoalRetryProjection(
      "Workflow artifact '$FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY' is malformed.",
    )
  val parentWorkflowId = continuation.parentWorkflowId ?: return null
  val parent = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, parentWorkflowId)
    ?: invalidGoalRetryProjection(
      "Goal child '$childWorkflowId' references unknown parent workflow '$parentWorkflowId'.",
    )
  val parentManifest = parent.decompositionRuntime(validator)
    ?: invalidGoalRetryProjection(
      "Goal parent '$parentWorkflowId' has no decomposition runtime artifact.",
    )
  if (parentManifest.issueKey != continuation.issueKey) {
    invalidGoalRetryProjection(
      "Goal child '$childWorkflowId' issue '${continuation.issueKey}' does not match parent " +
        "issue '${parentManifest.issueKey}'.",
    )
  }
  val retriedManifest = parentManifest.withRetriedSubtask(
    subtaskId = continuation.subtaskId,
    workflowId = childWorkflowId,
    lastResumableStep = phaseId,
  )
  val parentInput = WorkflowUpdateInput(
    workflowStatus = parent.workflowStatus,
    currentStepId = parent.currentStepId.orEmpty(),
    stepUpdates = null,
    artifactsPatch = WorkflowArtifactPatch.from(
      mapOf(
        DECOMPOSITION_RUNTIME_ARTIFACT_KEY to validator.encodeManifestWireMap(
          retriedManifest,
          DECOMPOSITION_RUNTIME_ARTIFACT_KEY,
        ),
      ),
    ),
    sessionId = parent.sessionId.orEmpty(),
    replaceArtifacts = true,
  )
  migrateLegacyGoalRunnerControls(unitOfWork, parent)
  val updatedParent = updateRecord(WorkflowFamily.TASK_RUNTIME.definition, parent, parentInput)
  WorkflowFamily.TASK_RUNTIME.saveRecord(
    unitOfWork.workflowStates,
    updatedParent.toRecord().copy(issueKey = retriedManifest.issueKey),
  )
  return updatedParent.artifactsJson
}

private fun invalidGoalRetryProjection(reason: String): Nothing = throw InvalidWorkflowStateSchemaError(reason)
