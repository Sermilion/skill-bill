package skillbill.application.decomposition.model

import skillbill.application.decomposition.DecompositionManifestWriter
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.WorkflowEngine
import java.nio.file.Path

internal data class RetryDecompositionManifestProjectionArgs(
  val database: DatabaseSessionFactory,
  val engine: WorkflowEngine,
  val decompositionManifestWriter: DecompositionManifestWriter,
  val decompositionManifestValidator: DecompositionManifestValidator,
  val decompositionManifestStore: DecompositionManifestStore,
  val repoRoot: Path,
  val workflowId: String,
)
