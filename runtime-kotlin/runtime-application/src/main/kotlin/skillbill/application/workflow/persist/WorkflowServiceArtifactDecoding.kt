package skillbill.application.workflow.persist

import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.taskruntime.artifact.phaseLedger
import skillbill.workflow.taskruntime.artifact.phaseRecords
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord

internal fun decodeFeatureTaskRuntimePhaseRecords(
  artifacts: DurableWorkflowArtifacts,
): Map<String, FeatureTaskRuntimePhaseRecord> = artifacts.phaseRecords()

internal object FeatureTaskRuntimePhaseLedgerDecoder {
  fun decode(artifacts: DurableWorkflowArtifacts): List<FeatureTaskRuntimePhaseLedgerEntry> = artifacts.phaseLedger()
}
