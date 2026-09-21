package skillbill.ports.codegraph.model

private const val MAX_COMMAND_OUTPUT_CHARS = 8_192

data class CodeGraphCommandResult(
  val exitCode: Int?,
  val stdout: String,
  val stderr: String,
  val started: Boolean = true,
) {
  init {
    require(stdout.length <= MAX_COMMAND_OUTPUT_CHARS) { "CodeGraph stdout exceeds the observation limit." }
    require(stderr.length <= MAX_COMMAND_OUTPUT_CHARS) { "CodeGraph stderr exceeds the observation limit." }
  }
}
