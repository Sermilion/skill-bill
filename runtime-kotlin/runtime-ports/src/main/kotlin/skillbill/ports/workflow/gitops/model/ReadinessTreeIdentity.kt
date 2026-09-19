package skillbill.ports.workflow.gitops.model

data class ReadinessTreeIdentity(
  val sourceTreeSha: String,
  val baseRefSha: String,
  val headSha: String,
)
