package skillbill.ports.decomposition

import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import java.nio.file.Path

interface DecompositionManifestProjectionWriter {
  fun writeProjectionFromWorkflowState(
    repoRoot: Path,
    artifacts: DurableWorkflowArtifacts,
    validator: DecompositionManifestValidator,
    fileStore: DecompositionManifestStore,
  ): DecompositionManifestProjectionOutcome
}
