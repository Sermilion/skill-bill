package skillbill.cli.core

import skillbill.cli.model.CliExecutionResult
import skillbill.cli.model.CliStdoutCompletion
import java.io.OutputStream

internal fun emitCliProcessStdout(result: CliExecutionResult, stdout: OutputStream) {
  when (result.stdoutCompletion) {
    CliStdoutCompletion.RAW -> {
      val bytes = result.rawStdout ?: byteArrayOf()
      stdout.write(bytes)
    }
    CliStdoutCompletion.EMPTY -> Unit
    CliStdoutCompletion.TEXT, CliStdoutCompletion.IMPLICIT -> {
      val text = result.stdout
      if (text.isNotEmpty()) {
        stdout.write(text.encodeToByteArray())
        if (!text.endsWith("\n")) {
          stdout.write(byteArrayOf('\n'.code.toByte()))
        }
      }
    }
  }
}
