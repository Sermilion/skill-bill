package skillbill.cli

import skillbill.ports.telemetry.model.RemoteTransportResponse
import skillbill.ports.telemetry.transport.RemoteTransportPort

internal class RecordingTelemetryRequester(
  private val failure: (() -> Nothing)? = null,
) : RemoteTransportPort {
  val requests: MutableList<String> = mutableListOf()

  override fun execute(
    method: String,
    url: String,
    bodyJson: String?,
    headers: Map<String, String>,
  ): RemoteTransportResponse {
    requests += url
    failure?.invoke()
    return RemoteTransportResponse(statusCode = 200, body = "{}")
  }
}
