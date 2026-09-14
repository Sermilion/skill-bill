package skillbill.workflow.taskruntime

interface FeatureTaskRuntimeQuarantineValidator {
  fun validateQuarantineRecord(quarantineRecord: Any, sourceLabel: String)
}
