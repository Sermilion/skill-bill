package skillbill.error.shellcontent

import skillbill.error.core.ShellContentContractException

class InvalidExperimentDescriptorSchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment descriptor '$sourceLabel' fails schema validation: $reason",
    cause,
  )

class InvalidExperimentPairSchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment pair '$sourceLabel' fails schema validation: $reason",
    cause,
  )

class InvalidExperimentObservationSchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment observation '$sourceLabel' fails schema validation: $reason",
    cause,
  )

class InvalidExperimentReportSchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment report '$sourceLabel' fails schema validation: $reason",
    cause,
  )

class ExperimentSelectionConflictError(
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment selection conflict: $reason",
    cause,
  )

class ExperimentParameterMalformedError(
  val parameter: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Malformed experiment parameter '$parameter': $reason",
    cause,
  )

class ExperimentConfigMalformedError(
  val path: String,
  val key: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Malformed experiment config at '$path' key '$key': $reason",
    cause,
  )

class ExperimentDescriptorUnavailableError(
  val requestedNames: Set<String>,
  val mode: String,
  val availableCompatible: Set<String>,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment selection unavailable for mode '$mode': requested=$requestedNames " +
      "compatible=$availableCompatible",
    cause,
  )

class ExperimentDirtySourceRefusalError(
  val dirtyPaths: List<String>,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment pair refused dirty source paths: ${dirtyPaths.joinToString(", ")}",
    cause,
  )

class ExperimentIsolationCapabilityRefusalError(
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment isolation capability refusal: $reason",
    cause,
  )

class ExperimentPairExecutionUnavailableError(
  val selectedNames: List<String>,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment pair execution is no longer supported by this runtime; " +
      "selection requested: ${selectedNames.joinToString(", ")}",
    cause,
  )

class ExperimentNavigationRevisionError(
  val revision: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Navigation experiment revision '$revision' is unavailable: $reason",
    cause,
  )

class ExperimentNavigationSpecError(
  val path: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Navigation experiment spec '$path' is invalid: $reason",
    cause,
  )

class ExperimentNavigationPairUnavailableError(
  val pairId: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Experiment navigation pair '$pairId' is not available.",
    cause,
  )
