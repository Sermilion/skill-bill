package skillbill.ports.workflow.decomposition

import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionManifestValidationResult
import skillbill.workflow.decomposition.model.DecompositionManifestWireMap

interface DecompositionManifestValidator {
  fun validate(
    manifest: DecompositionManifestWireMap,
    sourceLabel: String,
  )

  fun validateYamlText(
    yamlText: String,
    sourceLabel: String,
  ): DecompositionManifest

  fun validateYamlTextResult(
    yamlText: String,
    sourceLabel: String,
  ): DecompositionManifestValidationResult
}
