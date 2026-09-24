package skillbill.ports.workflow.gitops.readiness

import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.ReadinessTreeIdentity
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

interface ReadinessTreeIdentityGitOperations {
  fun resolveReadinessTreeIdentity(
    repoRoot: Path,
    baseBranch: String,
    workflowId: String,
  ): WorkflowGitOperationResult

  fun changedPathsAgainstBase(
    repoRoot: Path,
    baseBranch: String,
  ): WorkflowGitOperationResult
}

fun WorkflowGitOperations.resolveReadinessTreeIdentityPayload(
  repoRoot: Path,
  baseBranch: String,
  workflowId: String,
): WorkflowGitOperationResult =
  readinessTreeIdentityOperations.resolveReadinessTreeIdentity(
    repoRoot,
    baseBranch,
    workflowId,
  )

fun WorkflowGitOperations.resolveReadinessTreeIdentity(
  repoRoot: Path,
  baseBranch: String,
  workflowId: String,
): ReadinessTreeIdentity? {
  val result = resolveReadinessTreeIdentityPayload(repoRoot, baseBranch, workflowId)
  if (result !is WorkflowGitOperationResult.Ok) return null
  return decodeReadinessTreeIdentityPayload(result.value.orEmpty())
}

fun WorkflowGitOperations.readinessChangedPathsAgainstBase(
  repoRoot: Path,
  baseBranch: String,
): WorkflowGitOperationResult = readinessTreeIdentityOperations.changedPathsAgainstBase(repoRoot, baseBranch)

private const val IDENTITY_PART_COUNT = 3

fun encodeReadinessTreeIdentityPayload(identity: ReadinessTreeIdentity): String =
  listOf(identity.sourceTreeSha, identity.baseRefSha, identity.headSha).joinToString("\u0000")

fun decodeReadinessTreeIdentityPayload(payload: String): ReadinessTreeIdentity? {
  val parts = payload.split('\u0000')
  if (parts.size != IDENTITY_PART_COUNT) return null
  val sourceTreeSha = parts[0].trim()
  val baseRefSha = parts[1].trim()
  val headSha = parts[2].trim()
  if (sourceTreeSha.isBlank() || baseRefSha.isBlank() || headSha.isBlank()) return null
  return ReadinessTreeIdentity(sourceTreeSha, baseRefSha, headSha)
}
