package skillbill.ports.workflow.gitops.model

import java.nio.file.Path

data class LinkedWorktreeAddRequest(
  val repositoryRoot: Path,
  val worktreePath: Path,
  val branchName: String,
  val baseRef: String,
)

data class LinkedWorktreeRemoveRequest(
  val repositoryRoot: Path,
  val worktreePath: Path,
)
