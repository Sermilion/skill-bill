package skillbill.infrastructure.contracts.workflow.decomposition

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.decomposition.UnavailableDecompositionManifestStore
import skillbill.workflow.decomposition.model.DecompositionManifestWireMap

// Validator tests exercise manifest emission only; every file operation stays unavailable.
internal class YamlEncodingDecompositionManifestStore :
  DecompositionManifestStore by UnavailableDecompositionManifestStore {
  private val yamlMapper = YAMLMapper()

  override fun encodeManifestYaml(wireMap: DecompositionManifestWireMap): String =
    yamlMapper.writeValueAsString(wireMap)
}
