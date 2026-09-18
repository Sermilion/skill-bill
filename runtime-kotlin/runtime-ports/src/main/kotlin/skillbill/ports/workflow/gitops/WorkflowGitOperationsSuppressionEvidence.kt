package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.model.WorkflowScopedPathContentsResult
import java.nio.file.Path

interface SuppressionEvidenceGitOperations {
  fun scopedPathContentsAgainstBase(
    repoRoot: Path,
    baseRef: String,
    headPaths: List<String>,
  ): WorkflowScopedPathContentsResult
}
