package skillbill.engine.featuretask.lifecycle.continuation

import skillbill.application.workflow.model.WorkflowFamily
import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import skillbill.engine.featuretask.runloop.observability.continuation
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.get
import skillbill.ports.workflow.save
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_FIELD_ADOPTION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_OUTCOME_ARTIFACT_KEY
class FeatureTaskRuntimeGoalContinuationStateRecorder(
  private val database: DatabaseSessionFactory,
  private val engine: WorkflowEngine,
) {
  internal fun recordGoalContinuationState(request: GoalContinuationStateRecordRequest): Boolean =
    database.transaction { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, request.workflowId)
        ?: return@transaction false
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      val existingContinuation = continuationFromArtifacts(artifacts)
      val supplied = request.continuation?.let { continuation ->
        continuation.copy(subtaskName = continuation.subtaskName ?: existingContinuation?.subtaskName)
      }
      check(existingContinuation.compatibleWith(supplied)) {
        "Goal continuation is immutable for workflow '${request.workflowId}'; " +
          "parent, subtask, branch, and review mode cannot change on resume."
      }
      val continuationPatch = continuationPatch(supplied, existingContinuation)
      val reviewStatePatch = reviewStatePatch(request.copy(continuation = supplied), artifacts, existingContinuation)
      val outcomePatch = request.outcome?.let {
        mapOf(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_OUTCOME_ARTIFACT_KEY to it.toPersistenceWire())
      }.orEmpty()
      val adoptionPatch = request.fieldAdoption?.let {
        mapOf(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_FIELD_ADOPTION_ARTIFACT_KEY to it.asWorkflowArtifactEntry())
      }.orEmpty()
      val updated = engine.updateRecord(
        WorkflowFamily.TASK_RUNTIME.definition,
        record,
        WorkflowUpdateInput(
          workflowStatus = request.workflowStatus?.let { status ->
            WorkflowStatus.fromWire(status)
              ?: error("Unknown workflow status '$status'.")
          } ?: record.workflowStatus,
          currentStepId = request.outcome?.lastResumableStep ?: record.currentStepId,
          stepUpdates = null,
          artifactsPatch = WorkflowArtifactPatch.from(
            continuationPatch + reviewStatePatch + outcomePatch + adoptionPatch,
          ),
          sessionId = record.sessionId.orEmpty(),
        ),
      )
      WorkflowFamily.TASK_RUNTIME.save(unitOfWork.workflowStates, updated)
      true
    }

  fun reviewState(workflowId: String): GoalSubtaskReviewState? = database.read { unitOfWork ->
    val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) ?: return@read null
    reviewStateFromArtifacts(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
  }

  fun continuation(workflowId: String): FeatureTaskRuntimeGoalContinuationArtifact? = database.read { unitOfWork ->
    val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) ?: return@read null
    continuationFromArtifacts(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
  }
}
