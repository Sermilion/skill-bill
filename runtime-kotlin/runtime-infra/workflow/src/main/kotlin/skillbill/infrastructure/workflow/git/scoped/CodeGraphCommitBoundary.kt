package skillbill.infrastructure.workflow.git.scoped

import skillbill.codegraph.isCodeGraphGeneratedPath
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal fun codeGraphCommitBoundaryFailure(repoRoot: Path): WorkflowGitOperationResult? {
  val paths = runGitCommand(repoRoot, "ls-files", "-z")
  if (paths !is WorkflowGitOperationResult.Ok) return paths
  return if (paths.value.split('\u0000').any(::isCodeGraphGeneratedPath)) {
    WorkflowGitOperationResult.Failed(
      error = "Generated CodeGraph data is present in the index; remove it from the index before committing.",
    )
  } else {
    null
  }
}
