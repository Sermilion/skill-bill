package skillbill.error.core

import skillbill.error.shellcontent.ShellContentContractException

open class InvalidValidatorWireInputError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Validator wire input '${sourceLabel.ifBlank { "<unknown>" }}' is invalid: $reason",
    cause,
  )

class InvalidGovernedReviewEvidenceRequestError(
  val operation: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Governed review evidence request '${operation.ifBlank { "<unknown>" }}' is invalid: $reason",
    cause,
  )

class InvalidNativeAgentLinkInventoryDecodeError(
  val path: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Native-agent link inventory '${path.ifBlank { "<unknown>" }}' cannot be decoded: $reason",
    cause,
  )

class InvalidNativeAgentLinkInventoryWriteError(
  val path: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Native-agent link inventory '${path.ifBlank { "<unknown>" }}' cannot be written: $reason",
    cause,
  )

class InvalidNativeAgentLinkInventoryReconcileError(
  val path: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Native-agent link inventory '${path.ifBlank { "<unknown>" }}' cannot be reconciled: $reason",
    cause,
  )

class InvalidInstallStagingError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Install staging '${sourceLabel.ifBlank { "<unknown>" }}' failed: $reason",
    cause,
  )

class InvalidAgentAddonAgentIdError(
  val agentId: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Agent add-on agent id '${agentId.ifBlank { "<unknown>" }}' is invalid: $reason",
    cause,
  )
