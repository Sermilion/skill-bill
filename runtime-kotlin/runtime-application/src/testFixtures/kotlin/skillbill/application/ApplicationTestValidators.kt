package skillbill.application

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.JsonCodec
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.decodeManifest

val testDecompositionManifestValidator: DecompositionManifestValidator =
  object : DecompositionManifestValidator {
    override fun validate(manifest: Map<String, Any?>, sourceLabel: String) = Unit
    override fun validateYamlText(yamlText: String, sourceLabel: String) =
      decodeManifest(
        requireNotNull(JsonCodec.anyToStringAnyMap(YAMLMapper().readValue(yamlText, Map::class.java))),
        sourceLabel,
      )
  }
