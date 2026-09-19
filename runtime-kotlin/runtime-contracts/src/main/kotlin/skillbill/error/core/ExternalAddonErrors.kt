package skillbill.error.core

import skillbill.error.shellcontent.ShellContentContractException
class ExternalAddonConfigError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class ExternalAddonOverlayError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)
