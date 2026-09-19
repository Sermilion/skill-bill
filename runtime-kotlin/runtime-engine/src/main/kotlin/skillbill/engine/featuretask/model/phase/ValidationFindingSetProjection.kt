package skillbill.engine.featuretask.model.phase

import skillbill.ports.validation.model.ValidationGateFinding

data class ValidationFindingSetProjection(
  val findings: List<ValidationGateFinding>,
) {
  fun toHandoffMaps(): List<Map<String, String?>> = findings.map { finding ->
    linkedMapOf(
      "module" to finding.module,
      "rule_or_test_id" to finding.ruleOrTestId,
      "message" to finding.message,
      "location" to finding.location,
    )
  }
}
