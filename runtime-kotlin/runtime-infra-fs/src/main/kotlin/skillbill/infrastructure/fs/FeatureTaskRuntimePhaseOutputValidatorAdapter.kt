package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.workflow.FeatureTaskRuntimePhaseOutputSchemaValidator
import skillbill.workflow.FeatureTaskRuntimePhaseOutputValidator

/**
 * SKILL-65 Subtask 1: infra-side adapter that bridges the domain-owned
 * [FeatureTaskRuntimePhaseOutputValidator] port to the concrete
 * [FeatureTaskRuntimePhaseOutputSchemaValidator] owned by `runtime-infra-fs`.
 *
 * Mirrors [DecompositionManifestValidatorAdapter]. The schema validator parses
 * the phase output (JSON/YAML) and runs the canonical Draft 2020-12
 * validation, so the rest of the runtime reaches it only through this port.
 * Loud-fail behavior is unchanged: the delegate throws
 * [skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError] on
 * malformed input, a non-object root, empty `{}`, or any schema violation.
 */
@Inject
class FeatureTaskRuntimePhaseOutputValidatorAdapter : FeatureTaskRuntimePhaseOutputValidator {
  override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) {
    FeatureTaskRuntimePhaseOutputSchemaValidator.validatePhaseOutputText(phaseOutputText, sourceLabel)
  }
}
