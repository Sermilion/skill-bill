package skillbill.application

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.JsonCodec
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.decodeManifest
import skillbill.workflow.decomposition.model.DecompositionManifestValidationResult
import skillbill.workflow.decomposition.model.DecompositionManifestWireMap

val testDecompositionManifestValidator: DecompositionManifestValidator =
  object : DecompositionManifestValidator {
    override fun validate(
      manifest: DecompositionManifestWireMap,
      sourceLabel: String,
    ) = Unit

    override fun validateYamlText(
      yamlText: String,
      sourceLabel: String,
    ) = decodeManifest(
      DecompositionManifestWireMap.from(
        requireNotNull(JsonCodec.anyToStringAnyMap(YAMLMapper().readValue(yamlText, Map::class.java))),
      ),
      sourceLabel,
    )

    override fun validateYamlTextResult(
      yamlText: String,
      sourceLabel: String,
    ): DecompositionManifestValidationResult =
      DecompositionManifestValidationResult.AcceptedUnchanged(validateYamlText(yamlText, sourceLabel), yamlText)
  }
