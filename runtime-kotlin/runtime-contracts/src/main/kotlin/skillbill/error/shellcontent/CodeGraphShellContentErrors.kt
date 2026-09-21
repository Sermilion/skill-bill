package skillbill.error.shellcontent

class InvalidCodeGraphDependencySchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "CodeGraph dependency '$sourceLabel' fails schema validation: $reason",
  cause,
)

class InvalidCodeGraphQueryOutputSchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "CodeGraph query output '$sourceLabel' fails schema validation: $reason",
  cause,
)

class InvalidCodeGraphQueryReceiptSchemaError(
  val sourceLabel: String,
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "CodeGraph query receipt '$sourceLabel' fails schema validation: $reason",
  cause,
)

class CodeGraphInstallRefusalError(
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "CodeGraph install refused: $reason",
  cause,
)

class CodeGraphRetrievalRefusalError(
  val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "CodeGraph retrieval refused: $reason",
  cause,
)
