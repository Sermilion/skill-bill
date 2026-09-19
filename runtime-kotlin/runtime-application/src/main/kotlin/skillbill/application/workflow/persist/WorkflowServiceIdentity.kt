package skillbill.application.workflow.persist
import skillbill.application.workflow.decomposition.issueKey
import skillbill.application.workflow.decomposition.workflowId
import skillbill.application.workflow.model.BuildFeatureTaskExecutionIdentityArgs
import skillbill.application.workflow.model.WorkflowFamilyKind
import skillbill.application.workflow.service.FEATURE_TASK_FAMILY_KINDS
import skillbill.application.workflow.service.mode
import skillbill.application.workflow.service.workflowId
import skillbill.workflow.model.FeatureTaskExecutionIdentity
import skillbill.workflow.model.FeatureTaskExecutionIdentityPolicy
import skillbill.workflow.model.FeatureTaskWorkflowMode

fun hasIncompleteFeatureTaskIdentity(
  kind: WorkflowFamilyKind,
  hasIdentityCoordinates: Boolean,
  issueKey: String?,
  repositoryIdentity: String?,
  governedSpecPath: String?,
): Boolean = kind in FEATURE_TASK_FAMILY_KINDS &&
  hasIdentityCoordinates &&
  listOf(issueKey, repositoryIdentity, governedSpecPath).any { it == null }

fun buildFeatureTaskExecutionIdentity(args: BuildFeatureTaskExecutionIdentityArgs): FeatureTaskExecutionIdentity? {
  val kind = args.kind
  val hasIdentityCoordinates = args.hasIdentityCoordinates
  if (kind !in FEATURE_TASK_FAMILY_KINDS || !hasIdentityCoordinates) return null
  val requiredRepositoryIdentity = requireNotNull(args.repositoryIdentity)
  val normalizedIssueKey = FeatureTaskExecutionIdentityPolicy.validateLookupRequest(
    requireNotNull(args.issueKey),
    requiredRepositoryIdentity,
  )
  return FeatureTaskExecutionIdentity(
    workflowId = args.workflowId,
    normalizedIssueKey = normalizedIssueKey,
    repositoryIdentity = requiredRepositoryIdentity,
    governedSpecPath = requireNotNull(args.governedSpecPath),
    mode = FeatureTaskWorkflowMode.RUNTIME,
    routeScope = args.routeScope,
  ).also(FeatureTaskExecutionIdentityPolicy::validate)
}
