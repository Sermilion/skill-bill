package skillbill.error.shellcontent
import skillbill.error.core.error
import skillbill.error.core.message

class InvalidInstallPlanSchemaError(
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Install plan fails schema validation at '${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class InvalidNativeAgentCompositionSchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Native agent composition source '${sourceLabel.ifBlank { "<unknown>" }}' fails schema validation: $reason",
  cause,
)

class InvalidTelemetryEventSchemaError(
  val fieldPath: String,
  val eventName: String?,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Telemetry event '${eventName ?: "<unknown>"}' fails schema validation at " +
    "'${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class InvalidGoalObservabilityEventSchemaError(
  val sourceLabel: String,
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Goal observability event '${sourceLabel.ifBlank { "<unknown>" }}' fails schema validation at " +
    "'${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class InvalidGoalProgressEventSchemaError(
  val sourceLabel: String,
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Goal progress event '${sourceLabel.ifBlank { "<unknown>" }}' fails schema validation at " +
    "'${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class InvalidIdeStatusSchemaError(
  val sourceLabel: String,
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "IDE status '${sourceLabel.ifBlank { "<unknown>" }}' fails schema validation at " +
    "'${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class InvalidGoalSubtaskReviewStateSchemaError(
  val sourceLabel: String,
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Goal subtask review state '${sourceLabel.ifBlank { "<unknown>" }}' fails schema validation at " +
    "'${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class InvalidGoalPlanningPreparationSchemaError(
  val sourceLabel: String,
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Goal planning preparation '${sourceLabel.ifBlank { "<unknown>" }}' fails schema validation at " +
    "'${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class IncompatibleGoalPlanningPreparationRecoveryError(
  val workflowId: String,
  val subtaskId: Int,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Goal planning preparation '$workflowId' subtask $subtaskId cannot be recovered: $reason",
  cause,
)

class MissingInstallSelectionRecordError(
  val path: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Install selection record is missing at '${path.ifBlank { "<unknown>" }}'.",
  cause,
)

class UnreadableInstallSelectionRecordError(
  val path: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Install selection record at '${path.ifBlank { "<unknown>" }}' cannot be read.",
  cause,
)

class MalformedInstallSelectionRecordError(
  val path: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Install selection record at '${path.ifBlank { "<unknown>" }}' is malformed: $reason",
  cause,
)

class UnreadableBaselineManifestError(
  val path: String,
  val reason: String? = null,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Baseline manifest at '${path.ifBlank { "<unknown>" }}' cannot be read" +
    (reason?.let { ": $it." } ?: "."),
  cause,
)

class ReconciliationConflictError(
  val skillRelativePath: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Reconciliation failed for skill '${skillRelativePath.ifBlank { "<unknown>" }}': $reason",
  cause,
)

class UnreadableRepoLocalConfigError(
  val path: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Repo-local config at '${path.ifBlank { "<unknown>" }}' cannot be read.",
  cause,
)

class MalformedRepoLocalConfigError(
  val path: String,
  val key: String,
  val value: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Repo-local config at '${path.ifBlank { "<unknown>" }}' is malformed: " +
    "key '${key.ifBlank { "<root>" }}' value '$value' $reason",
  cause,
)

class MalformedMachineConfigError(
  val path: String,
  val key: String,
  val value: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Machine config at '${path.ifBlank { "<unknown>" }}' is malformed: " +
    "key '${key.ifBlank { "<root>" }}' value '$value' $reason",
  cause,
)

class ContractVersionMismatchError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)
