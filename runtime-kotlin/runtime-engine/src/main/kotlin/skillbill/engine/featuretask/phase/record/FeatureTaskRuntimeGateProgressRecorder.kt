package skillbill.engine.featuretask.phase.record

import skillbill.application.workflow.model.WorkflowFamily
import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.get
import skillbill.workflow.goal.model.GoalSubtaskReviewArtifactDecoder
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.decodeReadinessEvidenceFromArtifact
import skillbill.workflow.taskruntime.artifact.decodeValidationGateProgressFromArtifact
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.validation.FEATURE_TASK_RUNTIME_BUILD_GATE_PROGRESS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.validation.FEATURE_TASK_RUNTIME_READINESS_EVIDENCE_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.validation.FEATURE_TASK_RUNTIME_VALIDATION_GATE_PROGRESS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeReadinessEvidence
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateProgress

interface FeatureTaskRuntimeReadinessEvidencePort {
  fun loadReadinessEvidence(workflowId: String): FeatureTaskRuntimeReadinessEvidence?

  fun persistReadinessEvidence(workflowId: String, evidence: FeatureTaskRuntimeReadinessEvidence)
}

class FeatureTaskRuntimeGateProgressRecorder(
  private val database: DatabaseSessionFactory,
  private val workflowPersistence: FeatureTaskRuntimeWorkflowPersistence,
) : FeatureTaskRuntimeReadinessEvidencePort {
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

  override fun loadReadinessEvidence(workflowId: String): FeatureTaskRuntimeReadinessEvidence? =
    database.read { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) ?: return@read null
      val raw = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)[
        FEATURE_TASK_RUNTIME_READINESS_EVIDENCE_ARTIFACT_KEY,
      ]
      val artifact = JsonCodec.anyToStringAnyMap(raw) ?: return@read null
      decodeReadinessEvidenceFromArtifact(artifact, FEATURE_TASK_RUNTIME_READINESS_EVIDENCE_ARTIFACT_KEY)
    }

  override fun persistReadinessEvidence(workflowId: String, evidence: FeatureTaskRuntimeReadinessEvidence) {
    database.transaction { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
        ?: throw InvalidWorkflowStateSchemaError(
          "Cannot persist readiness evidence: workflow '$workflowId' is missing.",
        )
      workflowPersistence.persistArtifactsPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_READINESS_EVIDENCE_ARTIFACT_KEY to evidence.asWorkflowArtifactEntry()),
      )
    }
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
