package skillbill.error

open class InvalidWorkflowStateSchemaError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class ProseFeatureTaskWorkflowWriteRefusedError(
  val workflowId: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Feature-task workflow '$workflowId' write refused: mode=prose is retired. The prose engine " +
    "is deleted; re-run this feature on the runtime engine (mode=runtime) instead. Legacy " +
    "prose rows remain readable for history but no new prose writes are accepted.",
  cause,
)

class InvalidWorkListRowError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class WorkflowIssueKeyConflictError(
  val workflowId: String,
  val persistedIssueKey: String,
  val requestedIssueKey: String,
) : ShellContentContractException(
  "Workflow '$workflowId' is already associated with issue key '$persistedIssueKey', not '$requestedIssueKey'.",
)

class LegacyProseWorkflowError(
  val workflowId: String,
  val issueKey: String?,
) : ShellContentContractException(
  "Workflow '$workflowId' is a legacy prose-mode row; the prose engine is retired and this row " +
    "cannot be resumed, continued, or updated. Re-run this work through the runtime engine instead: " +
    "`skill-bill goal ${issueKey?.trim()?.ifEmpty { null } ?: "<ISSUE_KEY>"}`.",
)

class InvalidRejectedOutputDiagnosticSchemaError(message: String) :
  ShellContentContractException(message)

class InvalidProducerOutputEvidenceSchemaError(message: String) :
  ShellContentContractException(message)

class InvalidGoalPlanningDiscoveryExclusionsSchemaError(message: String) :
  ShellContentContractException(message)

class InvalidGoalVerificationBoundaryCapsSchemaError(message: String) :
  ShellContentContractException(message)

class InvalidIssueKeySchemaError(message: String) :
  ShellContentContractException(message)

class GoalVerificationBoundaryCapExceededError(message: String) :
  ShellContentContractException(message)

class InvalidDecompositionManifestSchemaError(
  val sourceLabel: String,
  val reason: String,
  val failureCode: String? = null,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Decomposition manifest '${sourceLabel.ifBlank { "<unknown>" }}' fails schema validation: $reason",
  cause,
)

class InvalidDecompositionManifestBundleJournalError(
  val sourceLabel: String,
  val reason: String,
  val failureCode: String? = null,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Decomposition manifest bundle journal '${sourceLabel.ifBlank { "<unknown>" }}' is invalid: $reason",
  cause,
)
