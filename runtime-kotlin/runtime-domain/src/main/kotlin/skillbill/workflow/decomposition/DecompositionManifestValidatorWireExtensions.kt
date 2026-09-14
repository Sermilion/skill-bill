package skillbill.workflow.decomposition

import skillbill.boundary.OpenBoundaryMap
import skillbill.workflow.decomposition.model.DecompositionManifest

@OpenBoundaryMap("Validated decomposition manifest wire decode seam")
fun DecompositionManifestValidator.decodeManifest(wireMap: Map<String, Any?>, sourceLabel: String): DecompositionManifest {
  validate(wireMap, sourceLabel)
  return DecompositionManifestWireCodec.decode(wireMap, sourceLabel)
}

@OpenBoundaryMap("Validated decomposition manifest wire encode seam")
fun DecompositionManifestValidator.encodeManifestWireMap(
  manifest: DecompositionManifest,
  sourceLabel: String = "<in-memory>",
): Map<String, Any?> {
  val wireMap = DecompositionManifestWireCodec.encode(manifest)
  validate(wireMap, sourceLabel)
  return wireMap
}
