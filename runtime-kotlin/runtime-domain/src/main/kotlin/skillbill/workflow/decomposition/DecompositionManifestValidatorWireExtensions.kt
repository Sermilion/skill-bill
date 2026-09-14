package skillbill.workflow.decomposition

import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.engine.model.DecompositionManifestWireMap

fun DecompositionManifestValidator.decodeManifest(
  wireMap: DecompositionManifestWireMap,
  sourceLabel: String,
): DecompositionManifest {
  validate(wireMap, sourceLabel)
  return DecompositionManifestWireCodec.decode(wireMap, sourceLabel)
}

fun DecompositionManifestValidator.encodeManifestWireMap(
  manifest: DecompositionManifest,
  sourceLabel: String = "<in-memory>",
): DecompositionManifestWireMap {
  val wireMap = DecompositionManifestWireMap.from(DecompositionManifestWireCodec.encode(manifest))
  validate(wireMap, sourceLabel)
  return wireMap
}
