package skillbill.cli.core

import skillbill.cli.model.CliExecutionResult
import skillbill.cli.model.CliStdoutCompletion
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CliProcessStdoutTest {
  @Test
  fun `raw completion writes exact bytes without trailing newline`() {
    val raw = byteArrayOf(0xff.toByte(), 0, 13, 10, 42)
    val out = ByteArrayOutputStream()
    emitCliProcessStdout(
      CliExecutionResult(
        exitCode = 0,
        stdout = "",
        rawStdout = raw,
        stdoutCompletion = CliStdoutCompletion.RAW,
      ),
      out,
    )
    assertContentEquals(raw, out.toByteArray())
  }

  @Test
  fun `text completion appends newline only when stdout lacks one`() {
    val out = ByteArrayOutputStream()
    emitCliProcessStdout(
      CliExecutionResult(
        exitCode = 0,
        stdout = "deleted=1",
        stdoutCompletion = CliStdoutCompletion.TEXT,
      ),
      out,
    )
    assertEquals("deleted=1\n", out.toString(Charsets.UTF_8))
  }

  @Test
  fun `empty completion emits no bytes`() {
    val out = ByteArrayOutputStream()

    emitCliProcessStdout(
      CliExecutionResult(
        exitCode = 0,
        stdout = "",
        stdoutCompletion = CliStdoutCompletion.EMPTY,
      ),
      out,
    )

    assertContentEquals(byteArrayOf(), out.toByteArray())
  }
}
