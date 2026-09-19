package skillbill.engine.featuretask.phase.record
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import java.time.Clock

fun featureTaskRuntimePhaseRecorder(
  database: DatabaseSessionFactory,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
  handoffEnvelopeValidator: FeatureTaskRuntimeWireArtifactValidator,
  handoffFoundationValidator: FeatureTaskRuntimeWireArtifactValidator,
  clock: Clock,
  diagnostics: RuntimeDiagnostics,
): FeatureTaskRuntimePhaseRecorder = FeatureTaskRuntimePhaseRecorder(
  database = database,
  workflowSnapshotValidator = workflowSnapshotValidator,
  wireArtifactValidator = handoffEnvelopeValidator,
  rejectedOutputDiagnosticMetadataValidator = { },
  producerOutputEvidenceValidator = { },
  diagnostics = diagnostics,
  clock = clock,
)
