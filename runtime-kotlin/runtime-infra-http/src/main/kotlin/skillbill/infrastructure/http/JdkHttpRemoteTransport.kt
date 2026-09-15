package skillbill.infrastructure.http

import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.telemetry.model.RemoteTransportResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

private const val DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS = 10L
private const val DEFAULT_HTTP_REQUEST_TIMEOUT_MINUTES = 4L

internal val DEFAULT_HTTP_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(DEFAULT_HTTP_CONNECT_TIMEOUT_SECONDS)
internal val DEFAULT_HTTP_REQUEST_TIMEOUT: Duration = Duration.ofMinutes(DEFAULT_HTTP_REQUEST_TIMEOUT_MINUTES)

class JdkHttpRemoteTransport(
  private val httpClient: HttpClient,
  private val requestTimeout: Duration,
) : RemoteTransportPort {
  override fun execute(
    method: String,
    url: String,
    bodyJson: String?,
    headers: Map<String, String>,
  ): RemoteTransportResponse {
    val requestBuilder =
      HttpRequest
        .newBuilder(URI.create(url))
        .timeout(requestTimeout)
        .method(method, bodyPublisher(bodyJson))
    headers.forEach(requestBuilder::header)
    val response = httpClient.send(requestBuilder.build(), BodyHandlers.ofString())
    return RemoteTransportResponse(statusCode = response.statusCode(), body = response.body().orEmpty())
  }

  companion object {
    private val defaultClient: HttpClient =
      HttpClient
        .newBuilder()
        .connectTimeout(DEFAULT_HTTP_CONNECT_TIMEOUT)
        .build()

    fun create(connectTimeout: Duration? = null, requestTimeout: Duration? = null): RemoteTransportPort =
      JdkHttpRemoteTransport(
        httpClient = httpClient(connectTimeout ?: DEFAULT_HTTP_CONNECT_TIMEOUT),
        requestTimeout = requestTimeout ?: DEFAULT_HTTP_REQUEST_TIMEOUT,
      )

    private fun httpClient(connectTimeout: Duration): HttpClient = if (connectTimeout == DEFAULT_HTTP_CONNECT_TIMEOUT) {
      defaultClient
    } else {
      HttpClient
        .newBuilder()
        .connectTimeout(connectTimeout)
        .build()
    }
  }
}

object JdkHttpRequester : RemoteTransportPort by JdkHttpRemoteTransport.create()

private fun bodyPublisher(bodyJson: String?): HttpRequest.BodyPublisher = if (bodyJson == null) {
  HttpRequest.BodyPublishers.noBody()
} else {
  HttpRequest.BodyPublishers.ofString(bodyJson)
}
