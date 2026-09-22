package skillbill.engine.decomposition

import skillbill.application.decomposition.encodeValidatedDecompositionManifestYaml
import skillbill.application.decomposition.model.DecompositionManifestFileCandidate
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.model.DecompositionManifest
import java.nio.file.Path
import skillbill.application.decomposition.findMatchingDecompositionManifests as applicationFindMatchingDecompositionManifests

internal fun encodeDecompositionManifestYaml(
  manifest: DecompositionManifest,
  validator: DecompositionManifestValidator,
  fileStore: DecompositionManifestStore,
  sourceLabel: String = "<in-memory>",
): String = encodeValidatedDecompositionManifestYaml(manifest, validator, fileStore, sourceLabel).yamlText

internal fun findMatchingDecompositionManifests(
  repoRoot: Path,
  issueKey: String,
  fileStore: DecompositionManifestStore,
  validator: DecompositionManifestValidator,
  recoverPending: Boolean = true,
): List<DecompositionManifestFileCandidate> =
  applicationFindMatchingDecompositionManifests(
    repoRoot = repoRoot,
    issueKey = issueKey,
    fileStore = fileStore,
    validator = validator,
    recoverPending = recoverPending,
  )
