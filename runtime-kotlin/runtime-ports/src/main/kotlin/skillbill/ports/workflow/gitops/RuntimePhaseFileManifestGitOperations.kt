package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.model.WorkflowGitNameListResult
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

interface RuntimePhaseFileManifestGitOperations {
  fun runtimePhaseHeadCommit(repoRoot: Path): WorkflowGitOperationResult

  fun runtimePhaseChangedPathsBetweenCommits(
    repoRoot: Path,
    beforeCommit: String,
    afterCommit: String,
  ): WorkflowGitNameListResult
}
