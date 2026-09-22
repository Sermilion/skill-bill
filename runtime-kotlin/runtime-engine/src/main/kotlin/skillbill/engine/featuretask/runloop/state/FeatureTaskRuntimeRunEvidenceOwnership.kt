package skillbill.engine.featuretask.runloop.state

import skillbill.engine.featuretask.lifecycle.checkpoint.normalizeForAliasComparison
import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeRunEvidenceAddress

object FeatureTaskRuntimeRunEvidenceOwnership {
  private val storeRoot = normalizeForAliasComparison(FeatureTaskRuntimeRunEvidenceAddress.STORE_ROOT)

  fun isRunEvidencePath(path: String): Boolean {
    val normalized = normalizeForAliasComparison(path)
    return normalized == storeRoot || normalized.startsWith("$storeRoot/")
  }

  fun isOwnedByRun(
    path: String,
    workflowId: String?,
  ): Boolean {
    if (workflowId.isNullOrBlank()) return false
    val owned = normalizeForAliasComparison(FeatureTaskRuntimeRunEvidenceAddress.workflowStoreRoot(workflowId))
    val normalized = normalizeForAliasComparison(path)
    return normalized == owned || normalized.startsWith("$owned/")
  }
}
