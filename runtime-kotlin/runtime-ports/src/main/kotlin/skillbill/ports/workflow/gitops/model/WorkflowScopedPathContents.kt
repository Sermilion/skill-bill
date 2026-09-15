package skillbill.ports.workflow.gitops.model

data class WorkflowScopedPathContent(
  val headPath: String,
  val basePath: String?,
  val headContent: String?,
  val baseContent: String?,
)

data class WorkflowScopedPathContentsResult(
  val status: WorkflowGitOperationStatus,
  val pairs: List<WorkflowScopedPathContent> = emptyList(),
  val error: String = "",
)
