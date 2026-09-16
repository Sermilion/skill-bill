package skillbill.error

class MalformedJsonTextError(cause: Throwable) : ShellContentContractException(
  "JSON text is malformed: ${cause.message.orEmpty()}",
  cause,
)

class JsonWrongRootTypeError(expectedRoot: String) : ShellContentContractException(
  "JSON root must be $expectedRoot",
)

class UnsupportedJsonValueError(message: String) : ShellContentContractException(message)

class JsonIntegerOutOfRangeError(message: String) : ShellContentContractException(message)
