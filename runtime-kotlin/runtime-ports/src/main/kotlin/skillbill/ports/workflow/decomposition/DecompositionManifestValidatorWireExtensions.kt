package skillbill.ports.workflow.decomposition

import skillbill.workflow.decomposition.decodeDecompositionManifestWireMap
import skillbill.workflow.decomposition.encodeDecompositionManifestWireMap
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionManifestWireMap

fun DecompositionManifestValidator.decodeManifest(
  wireMap: DecompositionManifestWireMap,
  sourceLabel: String,
): DecompositionManifest {
  validate(wireMap, sourceLabel)
  return decodeDecompositionManifestWireMap(wireMap, sourceLabel)
}

fun DecompositionManifestValidator.encodeManifestWireMap(
  manifest: DecompositionManifest,
  sourceLabel: String = "<in-memory>",
): DecompositionManifestWireMap {
  val wireMap = encodeDecompositionManifestWireMap(manifest)
  validate(wireMap, sourceLabel)
  return wireMap
}
