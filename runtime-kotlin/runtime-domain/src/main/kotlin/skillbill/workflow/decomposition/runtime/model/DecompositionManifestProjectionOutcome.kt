package skillbill.workflow.decomposition.runtime.model

sealed interface DecompositionManifestProjectionOutcome {
  data object Absent : DecompositionManifestProjectionOutcome

  data class Written(
    val result: DecompositionManifestWriteResult,
  ) : DecompositionManifestProjectionOutcome

  data class Failed(
    val operation: String,
    val targetPath: String,
  ) : DecompositionManifestProjectionOutcome
}
