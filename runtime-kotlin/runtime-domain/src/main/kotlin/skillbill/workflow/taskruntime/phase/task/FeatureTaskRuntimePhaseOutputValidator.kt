package skillbill.workflow.taskruntime.phase.task
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputValidationResult

interface FeatureTaskRuntimePhaseOutputValidator {
  fun validatePhaseOutput(phaseOutputText: String, sourceLabel: String): FeatureTaskRuntimePhaseOutputValidationResult

  fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String)

  fun validateAndReadPhaseOutput(phaseOutputText: String, sourceLabel: String): Any

  fun normalizePhaseOutput(phaseOutputText: String, sourceLabel: String): NormalizedFeatureTaskRuntimePhaseOutput
}
