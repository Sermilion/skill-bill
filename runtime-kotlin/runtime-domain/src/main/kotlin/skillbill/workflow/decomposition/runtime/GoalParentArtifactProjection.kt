package skillbill.workflow.decomposition.runtime

import skillbill.goalrunner.GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY
import skillbill.goalrunner.GOAL_REVIEW_POLICY_ARTIFACT_KEY

fun goalParentArtifactProjection(
  existing: Map<String, Any?>,
  encodedManifest: Map<String, Any?>,
): Map<String, Any?> =
  LinkedHashMap(existing).apply {
    remove(GOAL_REVIEW_POLICY_ARTIFACT_KEY)
    remove(GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY)
    put(DECOMPOSITION_RUNTIME_ARTIFACT_KEY, encodedManifest)
  }
