package skillbill.cli.model

data class CliExecutionResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String = "",
  val payload: Map<String, Any?>? = null,
  val rawStdout: ByteArray? = null,
  val stdoutCompletion: CliStdoutCompletion = CliStdoutCompletion.IMPLICIT,
)
