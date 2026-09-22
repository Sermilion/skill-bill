package skillbill.infrastructure.contracts.phaseoutput

import com.fasterxml.jackson.databind.JsonNode
import skillbill.error.featuretask.FeatureTaskRuntimePhaseOutputFailureCode
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputSourceLocation

internal sealed interface FeatureTaskRuntimePhaseOutputStructuralRepairDecision {
  data class Accepted(
    val text: String,
    val node: JsonNode,
    val evidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  ) : FeatureTaskRuntimePhaseOutputStructuralRepairDecision

  data class Rejected(
    val code: FeatureTaskRuntimePhaseOutputFailureCode,
    val reason: String,
    val sourceLocation: FeatureTaskRuntimePhaseOutputSourceLocation? = null,
  ) : FeatureTaskRuntimePhaseOutputStructuralRepairDecision
}
