package skillbill.error.core

import skillbill.error.shellcontent.ShellContentContractException

class ExternalPlatformPackConfigError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class AmbiguousExternalPlatformPackError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class ExternalPlatformPackOverlayError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class ExternalPlatformPackPublishError(
  message: String,
  val remotePayload: Map<String, String>,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)
