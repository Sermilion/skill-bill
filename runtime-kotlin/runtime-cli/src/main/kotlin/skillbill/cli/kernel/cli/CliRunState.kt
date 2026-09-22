package skillbill.cli.kernel.cli
import skillbill.cli.model.CliExecutionResult
import skillbill.cli.model.CliFormat
import skillbill.cli.model.CliStdoutCompletion

class CliRunState(private val stdinText: String?) {
  var result: CliExecutionResult? = null
  private var stdinLineIterator: Iterator<String>? = null
  private var wholeStdinCached: String? = null

  fun complete(
    payload: Map<String, Any?>,
    format: CliFormat,
    exitCode: Int = 0,
  ) {
    result =
      CliExecutionResult(
        exitCode = exitCode,
        stdout = CliOutput.emit(payload, format),
        payload = payload,
        stdoutCompletion = CliStdoutCompletion.TEXT,
      )
  }

  fun completeText(
    stdout: String,
    payload: Map<String, Any?>,
    exitCode: Int = 0,
  ) {
    result =
      CliExecutionResult(
        exitCode = exitCode,
        stdout = stdout,
        payload = payload,
        stdoutCompletion = CliStdoutCompletion.TEXT,
      )
  }

  fun completeRaw(
    rawStdout: ByteArray,
    exitCode: Int = 0,
  ) {
    result =
      CliExecutionResult(
        exitCode = exitCode,
        stdout = "",
        rawStdout = rawStdout,
        stdoutCompletion = CliStdoutCompletion.RAW,
      )
  }

  fun completeEmpty(exitCode: Int = 0) {
    result =
      CliExecutionResult(
        exitCode = exitCode,
        stdout = "",
        stdoutCompletion = CliStdoutCompletion.EMPTY,
      )
  }

  fun wholeStdinText(): String =
    wholeStdinCached ?: run {
      val consumed = stdinText ?: System.`in`.bufferedReader().use { it.readText() }
      wholeStdinCached = consumed
      consumed
    }

  fun readInputLine(): String? {
    val text = stdinText
    if (text != null) {
      val iterator = stdinLineIterator ?: text.lineSequence().iterator().also { stdinLineIterator = it }
      return if (iterator.hasNext()) iterator.next() else null
    }
    return readlnOrNull()
  }
}
