package skillbill.engine.featuretask.model.subtask
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeFeatureSize
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
data class CompletedUpstreamRepairRequest(
  val phaseRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  val ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  val featureSize: FeatureTaskRuntimeFeatureSize,
  val resumePhaseId: String,
  val reason: String,
  val qualityGateSelection: FeatureTaskRuntimeQualityGateSelection =
    FeatureTaskRuntimeQualityGateSelection.VALIDATE,
)
