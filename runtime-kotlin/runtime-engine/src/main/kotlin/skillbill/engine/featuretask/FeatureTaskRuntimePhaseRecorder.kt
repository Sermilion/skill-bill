package skillbill.engine.featuretask

import me.tatarka.inject.annotations.Inject
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.ProducerOutputEvidenceValidator
import skillbill.ports.diagnostics.RejectedOutputDiagnosticMetadataValidator
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import java.time.Clock

private class FeatureTaskRuntimePhaseRecorderParts(
  database: DatabaseSessionFactory,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
  wireArtifactValidator: FeatureTaskRuntimeWireArtifactValidator,
  rejectedOutputDiagnosticMetadataValidator: RejectedOutputDiagnosticMetadataValidator,
  producerOutputEvidenceValidator: ProducerOutputEvidenceValidator,
  diagnostics: RuntimeDiagnostics,
  clock: Clock,
) {
  val workflowPersistence = FeatureTaskRuntimeWorkflowPersistence(database, workflowSnapshotValidator)
  val runtimeOwnedPersistence = RuntimeOwnedPersistenceBoundary(database, diagnostics)
  val rejectedOutput = FeatureTaskRuntimeRejectedOutputRecorder(
    database,
    workflowPersistence,
    rejectedOutputDiagnosticMetadataValidator,
    producerOutputEvidenceValidator,
    clock,
  )
  val phaseState = FeatureTaskRuntimePhaseStateRecorder(
    database,
    workflowPersistence,
    runtimeOwnedPersistence,
    wireArtifactValidator,
    clock,
  )
  val reviewCheckpoint = FeatureTaskRuntimeReviewCheckpointRecorder(
    database,
    workflowPersistence,
    runtimeOwnedPersistence,
  )
  val goalReviewCompletion = FeatureTaskRuntimeGoalReviewCompletionRecorder(database, workflowPersistence, clock)
  val briefingRecorder = FeatureTaskRuntimePhaseBriefingRecorder(
    database,
    workflowPersistence,
    wireArtifactValidator,
  )
  val gateProgress = FeatureTaskRuntimeGateProgressRecorder(database, workflowPersistence)
  val evidence = FeatureTaskRuntimePhaseEvidenceRecorder(
    database,
    workflowPersistence,
    wireArtifactValidator,
    clock,
  )
}

class FeatureTaskRuntimePhaseRecorder private constructor(
  parts: FeatureTaskRuntimePhaseRecorderParts,
) : FeatureTaskRuntimePhaseWorkflowApi by parts.workflowPersistence,
  FeatureTaskRuntimePhaseRejectedApi by parts.rejectedOutput,
  FeatureTaskRuntimePhaseStateApi by parts.phaseState,
  FeatureTaskRuntimePhaseReviewApi by parts.goalReviewCompletion,
  FeatureTaskRuntimePhaseReviewCheckpointApi by parts.reviewCheckpoint,
  FeatureTaskRuntimePhaseBriefingApi by parts.briefingRecorder,
  FeatureTaskRuntimePhaseGateApi by parts.gateProgress,
  FeatureTaskRuntimePhaseEvidenceApi by parts.evidence {
  @Inject
  constructor(
    database: DatabaseSessionFactory,
    workflowSnapshotValidator: WorkflowSnapshotValidator,
    wireArtifactValidator: FeatureTaskRuntimeWireArtifactValidator,
    rejectedOutputDiagnosticMetadataValidator: RejectedOutputDiagnosticMetadataValidator,
    producerOutputEvidenceValidator: ProducerOutputEvidenceValidator,
    diagnostics: RuntimeDiagnostics,
    clock: Clock,
  ) : this(
    FeatureTaskRuntimePhaseRecorderParts(
      database = database,
      workflowSnapshotValidator = workflowSnapshotValidator,
      wireArtifactValidator = wireArtifactValidator,
      rejectedOutputDiagnosticMetadataValidator = rejectedOutputDiagnosticMetadataValidator,
      producerOutputEvidenceValidator = producerOutputEvidenceValidator,
      diagnostics = diagnostics,
      clock = clock,
    ),
  )
}
