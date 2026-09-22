package skillbill.infrastructure.contracts.phaseoutput

import com.fasterxml.jackson.databind.JsonNode
import skillbill.error.featuretask.FeatureTaskRuntimePhaseOutputFailureCode
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputSourceLocation

internal object StructuralRepairDecisions {
  fun accepted(
    text: String,
    node: JsonNode,
    evidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  ): FeatureTaskRuntimePhaseOutputStructuralRepairDecision =
    FeatureTaskRuntimePhaseOutputStructuralRepairDecision.Accepted(text, node, evidence)

  fun reject(
    code: FeatureTaskRuntimePhaseOutputFailureCode,
    reason: String,
    sourceLocation: FeatureTaskRuntimePhaseOutputSourceLocation? = null,
  ): FeatureTaskRuntimePhaseOutputStructuralRepairDecision =
    FeatureTaskRuntimePhaseOutputStructuralRepairDecision.Rejected(code, reason, sourceLocation)
}
