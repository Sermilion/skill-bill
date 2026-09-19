package skillbill.engine.featuretask.phase.record




import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import skillbill.application.workflow.model.WorkflowFamily
import skillbill.contracts.JsonCodec
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.get
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifactDecoder
import skillbill.workflow.taskruntime.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.decodeValidationGateProgressFromArtifact
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_BUILD_GATE_PROGRESS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_VALIDATION_GATE_PROGRESS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateProgress

class FeatureTaskRuntimeGateProgressRecorder(
  private val database: DatabaseSessionFactory,
  private val workflowPersistence: FeatureTaskRuntimeWorkflowPersistence,
) {
  fun loadValidationGateProgress(workflowId: String): FeatureTaskRuntimeValidationGateProgress? =
    database.read { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) ?: return@read null
      val raw = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)[
        FEATURE_TASK_RUNTIME_VALIDATION_GATE_PROGRESS_ARTIFACT_KEY,
      ]
      val artifact = JsonCodec.anyToStringAnyMap(raw) ?: return@read null
      decodeValidationGateProgressFromArtifact(artifact)
    }

  fun persistValidationGateProgress(workflowId: String, progress: FeatureTaskRuntimeValidationGateProgress) {
    database.transaction { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
        ?: throw InvalidWorkflowStateSchemaError(
          "Cannot persist validation gate progress: workflow '$workflowId' is missing.",
        )
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_VALIDATION_GATE_PROGRESS_ARTIFACT_KEY to progress.asWorkflowArtifactEntry()),
      )
    }
  }

  fun loadBuildGateProgress(workflowId: String): FeatureTaskRuntimeValidationGateProgress? =
    database.read { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) ?: return@read null
      val raw = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)[
        FEATURE_TASK_RUNTIME_BUILD_GATE_PROGRESS_ARTIFACT_KEY,
      ]
      val artifact = JsonCodec.anyToStringAnyMap(raw) ?: return@read null
      decodeValidationGateProgressFromArtifact(artifact)
    }

  fun loadGoalContinuationQualityGateSelection(workflowId: String): FeatureTaskRuntimeQualityGateSelection? =
    database.read { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) ?: return@read null
      GoalSubtaskReviewArtifactDecoder.decodeContinuationOnly(
        FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record),
      )
        ?.qualityGateSelection
    }

  fun persistBuildGateProgress(workflowId: String, progress: FeatureTaskRuntimeValidationGateProgress) {
    database.transaction { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
        ?: throw InvalidWorkflowStateSchemaError(
          "Cannot persist build gate progress: workflow '$workflowId' is missing.",
        )
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_BUILD_GATE_PROGRESS_ARTIFACT_KEY to progress.asWorkflowArtifactEntry()),
      )
    }
  }
}
