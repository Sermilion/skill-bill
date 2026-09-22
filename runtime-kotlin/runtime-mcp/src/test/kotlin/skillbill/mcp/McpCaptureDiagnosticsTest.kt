package skillbill.mcp

import skillbill.mcp.shared.recordCaptureFailure
import skillbill.ports.diagnostics.RuntimeDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class McpCaptureDiagnosticsTest {
  @Test
  fun `capture failure records one diagnostic naming the tool`() {
    var count = 0
    var diagnosticMessage = ""
    var captured: Throwable? = null
    val diagnostics =
      object : RuntimeDiagnostics {
        override fun warning(
          message: String,
          error: Throwable?,
        ) = Unit

        override fun error(
          message: String,
          error: Throwable?,
        ) {
          count += 1
          captured = error
          diagnosticMessage = message
        }
      }
    val failure = IllegalStateException("capture failed")

    recordCaptureFailure("quality_check_finished", { throw failure }, diagnostics)

    assertEquals(1, count)
    assertEquals("MCP telemetry capture failed for tool 'quality_check_finished'.", diagnosticMessage)
    assertSame(failure, captured)
  }
}
