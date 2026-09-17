package skillbill.workflow.taskruntime

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputValidationResult
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput

interface FeatureTaskRuntimePhaseOutputValidator {
  fun validatePhaseOutput(phaseOutputText: String, sourceLabel: String): FeatureTaskRuntimePhaseOutputValidationResult

  fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String)

  fun validateAndReadPhaseOutput(phaseOutputText: String, sourceLabel: String): Any

  fun normalizePhaseOutput(phaseOutputText: String, sourceLabel: String): NormalizedFeatureTaskRuntimePhaseOutput
}
