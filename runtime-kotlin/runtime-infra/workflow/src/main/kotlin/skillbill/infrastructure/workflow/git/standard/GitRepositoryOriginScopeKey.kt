package skillbill.infrastructure.workflow.git.standard

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.learnings.normalizeRepoScopeKey
import skillbill.ports.repository.RepositoryOriginScopeKeyPort
import skillbill.ports.repository.model.OriginScopeKey
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

@Inject
class GitRepositoryOriginScopeKey : RepositoryOriginScopeKeyPort {
  override fun resolveOriginScopeKey(repoRoot: Path): OriginScopeKey {
    val result = runGitCommand(repoRoot, "remote", "get-url", "origin")
    if (result !is WorkflowGitOperationResult.Ok) {
      return OriginScopeKey.Unavailable("git remote get-url origin did not succeed for '$repoRoot'")
    }
    val originUrl = result.value.trim()
    if (originUrl.isEmpty()) {
      return OriginScopeKey.Unavailable("git remote get-url origin returned no URL for '$repoRoot'")
    }
    val scopeKey =
      normalizeRepoScopeKey(originUrl)
        ?: return OriginScopeKey.Unavailable("origin URL '$originUrl' does not normalize to a repository path")
    return OriginScopeKey.Resolved(scopeKey)
  }
}
