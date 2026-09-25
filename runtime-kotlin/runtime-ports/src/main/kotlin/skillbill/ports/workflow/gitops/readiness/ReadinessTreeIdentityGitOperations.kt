package skillbill.ports.workflow.gitops.readiness

import skillbill.ports.workflow.gitops.model.WorkflowGitNameListResult
import skillbill.ports.workflow.gitops.model.WorkflowReadinessTreeIdentityResult
import java.nio.file.Path

interface ReadinessTreeIdentityGitOperations {
  fun resolveReadinessTreeIdentity(
    repoRoot: Path,
    baseBranch: String,
    workflowId: String,
  ): WorkflowReadinessTreeIdentityResult

  fun readinessChangedPathsAgainstBase(
    repoRoot: Path,
    baseBranch: String,
  ): WorkflowGitNameListResult
}
