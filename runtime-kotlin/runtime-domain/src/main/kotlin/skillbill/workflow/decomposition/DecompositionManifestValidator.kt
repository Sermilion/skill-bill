package skillbill.workflow.decomposition

import skillbill.error.InvalidDecompositionManifestSchemaError
import skillbill.workflow.engine.model.DecompositionManifestWireMap
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionManifestValidationFailureCode
import skillbill.workflow.decomposition.model.DecompositionManifestValidationResult

/**
 * SKILL-52.3 Subtask 1: domain-owned validator port for decomposition
 * manifest wire payloads.
 *
 * Mirrors [WorkflowSnapshotValidator]: the concrete schema + coherence
 * validators now live in `runtime-infra-fs`, and `runtime-application`
 * reaches them only through this port. Implementations MUST run the
 * canonical JSON-Schema validation followed by the Kotlin-enforced
 * coherence checks, throwing [InvalidDecompositionManifestSchemaError] on
 * any violation so every parse/emission seam stays loud.
 *
 * The `sourceLabel` is the caller-supplied identifier (on-disk path or an
 * in-memory marker) woven into the loud-fail message.
 */
interface DecompositionManifestValidator {
  /**
   * Validates a decomposition-manifest map against the canonical schema
   * and coherence rules. Throws [InvalidDecompositionManifestSchemaError]
   * on any violation.
   */
  fun validate(manifest: DecompositionManifestWireMap, sourceLabel: String)

  fun validateYamlText(yamlText: String, sourceLabel: String): DecompositionManifest

  /**
   * Returns the versioned result at the YAML parse/repair boundary. The default keeps existing
   * test doubles source-compatible; infrastructure adapters override it to add bounded syntax
   * repair and repair evidence.
   */
  fun validateYamlTextResult(yamlText: String, sourceLabel: String): DecompositionManifestValidationResult = try {
    DecompositionManifestValidationResult.AcceptedUnchanged(
      manifest = validateYamlText(yamlText, sourceLabel),
      yamlText = yamlText,
    )
  } catch (error: InvalidDecompositionManifestSchemaError) {
    DecompositionManifestValidationResult.Rejected(
      code = DecompositionManifestValidationFailureCode.fromWire(error.failureCode),
      reason = error.reason,
    )
  }
}
