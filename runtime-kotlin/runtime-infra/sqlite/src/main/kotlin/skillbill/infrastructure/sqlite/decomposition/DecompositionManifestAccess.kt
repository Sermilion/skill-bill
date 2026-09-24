package skillbill.infrastructure.sqlite.decomposition

import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator
import skillbill.ports.workflow.decomposition.runtime.model.LoadedDecompositionManifest
import skillbill.ports.workflow.decomposition.runtime.model.ValidatedDecompositionManifestYaml
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionManifestValidationResult
import skillbill.workflow.decomposition.model.requireAccepted
import skillbill.workflow.model.DecompositionStatus
import skillbill.workflow.model.decompositionStatus
import java.nio.file.Path

internal fun loadValidatedDecompositionManifest(
  path: Path,
  fileStore: DecompositionManifestStore,
  validator: DecompositionManifestValidator,
  recoverPending: Boolean = true,
): LoadedDecompositionManifest {
  val validated = validateDecompositionManifestYaml(path, fileStore, validator, recoverPending)
  return LoadedDecompositionManifest(
    manifest = validated.manifest,
    yamlText = validated.yamlText,
    repairEvidence = validated.repairEvidence,
  )
}

internal fun validateDecompositionManifestYaml(
  path: Path,
  fileStore: DecompositionManifestStore,
  validator: DecompositionManifestValidator,
  recoverPending: Boolean = true,
): ValidatedDecompositionManifestYaml {
  val yamlText = if (recoverPending) fileStore.readText(path) else fileStore.readTextWithoutRecovery(path)
  return when (val result = validator.validateYamlTextResult(yamlText, path.toString())) {
    is DecompositionManifestValidationResult.AcceptedUnchanged ->
      ValidatedDecompositionManifestYaml(
        manifest = result.manifest,
        yamlText = result.yamlText,
        repairEvidence = null,
      )
    is DecompositionManifestValidationResult.AcceptedAfterRepair ->
      ValidatedDecompositionManifestYaml(
        manifest = result.manifest,
        yamlText = result.yamlText,
        repairEvidence = result.evidence,
      )
    is DecompositionManifestValidationResult.Rejected -> {
      result.requireAccepted(path.toString())
      error("Unreachable rejected decomposition manifest result.")
    }
  }
}

internal fun DecompositionManifest.withParentStatus(): DecompositionManifest {
  val parentStatus =
    when {
      subtasks.all {
        it.status.decompositionStatus() in setOf(DecompositionStatus.COMPLETE, DecompositionStatus.SKIPPED)
      } -> DecompositionStatus.COMPLETE.wireValue
      subtasks.any { it.status.decompositionStatus() == DecompositionStatus.BLOCKED } ->
        DecompositionStatus.BLOCKED.wireValue
      subtasks.any {
        it.status.decompositionStatus() in
          setOf(
            DecompositionStatus.IN_PROGRESS,
            DecompositionStatus.COMPLETE,
            DecompositionStatus.SKIPPED,
          ) || it.hasStarted()
      } -> DecompositionStatus.IN_PROGRESS.wireValue
      else -> DecompositionStatus.PENDING.wireValue
    }
  return copy(status = parentStatus)
}

internal fun Any?.asStringAnyMapOrNull(): Map<String, Any?>? =
  (this as? Map<*, *>)?.entries?.associateTo(LinkedHashMap()) { (key, value) ->
    val stringKey = key as? String ?: return null
    stringKey to value
  }
