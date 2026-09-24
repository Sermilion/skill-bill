package skillbill.error.shellcontent

import skillbill.error.core.ShellContentContractException

class MissingManifestError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidManifestSchemaError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidValidationGateDeclarationError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class MissingValidationGateError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class ReviewCompositionCycleError(message: String) : ShellContentContractException(message)

class AmbiguousLaneOwnershipError(message: String) : ShellContentContractException(message)

class IncompatibleCompositionContractError(message: String) : ShellContentContractException(message)

class MissingCompositionLayerError(message: String) : ShellContentContractException(message)
