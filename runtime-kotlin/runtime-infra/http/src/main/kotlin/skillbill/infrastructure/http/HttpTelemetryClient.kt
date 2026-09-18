package skillbill.infrastructure.http

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.RemoteStatsQueryPayload
import skillbill.contracts.telemetry.defaultProxyCapabilities
import skillbill.contracts.time.JvmSystemClock
import skillbill.model.EnvironmentContext
import skillbill.model.TransportContext
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.telemetry.TelemetryClient
import skillbill.ports.telemetry.model.RemoteTransportResponse
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.telemetry.TELEMETRY_PROXY_CONTRACT_VERSION
import skillbill.telemetry.TELEMETRY_PROXY_STATS_TOKEN_ENVIRONMENT_KEY
import skillbill.telemetry.model.RemoteStatsRequest
import skillbill.telemetry.model.TelemetryDeliveryReport
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult
import skillbill.telemetry.model.TelemetrySettings
import skillbill.telemetry.parseRemoteStatsWindow
import skillbill.telemetry.validateIngestCapabilities
import skillbill.telemetry.validateRemoteStatsCapabilities
import skillbill.telemetry.validateRemoteStatsRequest
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@Inject
class HttpTelemetryClient(
  private val environmentContext: EnvironmentContext,
  private val transportContext: TransportContext,
  private val clock: Clock,
) : TelemetryClient {
  private val resolvedEnvironment = environmentContext.withProcessDefaults()
  private val resolvedRequester =
    transportContext.requester
      ?: JdkHttpRemoteTransport.create(transportContext.connectTimeout, transportContext.requestTimeout)

  constructor(
    requester: RemoteTransportPort,
    environment: Map<String, String> = System.getenv(),
  ) : this(requester, environment, Path.of(System.getProperty("user.home")))

  constructor(
    requester: RemoteTransportPort,
    environment: Map<String, String>,
    userHome: Path,
  ) : this(
    EnvironmentContext(environment = environment, userHome = userHome),
    TransportContext(requester = requester),
    JvmSystemClock,
  )

  override fun sendBatch(settings: TelemetrySettings, rows: List<TelemetryOutboxRecord>): TelemetryDeliveryReport {
    require(settings.proxyUrl.isNotBlank()) { "Telemetry relay URL is not configured." }
    val response =
      resolvedRequester.execute(
        "POST",
        settings.proxyUrl,
        JsonCodec.mapToJsonString(telemetryProxyBatchPayload(settings, rows).toPayload()),
        defaultJsonHeaders(),
      )
    return TelemetryDeliveryReport(
      outcome = classifyDeliveryOutcome(response.statusCode),
      detail = deliveryDetail(response),
    )
  }

  override fun fetchProxyCapabilities(settings: TelemetrySettings): TelemetryProxyCapabilities {
    require(settings.proxyUrl.isNotBlank()) { "Telemetry relay URL is not configured." }
    val capabilitiesUrl = settings.proxyUrl.trimEnd('/') + "/capabilities"
    return try {
      requestJsonGet(
        url = capabilitiesUrl,
        errorContext = "Telemetry proxy capabilities request",
        headers = proxyAuthHeaders(resolvedEnvironment.environment),
        requester = resolvedRequester,
      ).toMutableMap().apply {
        putIfAbsent("contract_version", TELEMETRY_PROXY_CONTRACT_VERSION)
        putIfAbsent("source", "remote_proxy")
        putIfAbsent("proxy_url", settings.proxyUrl)
        putIfAbsent("capabilities_url", capabilitiesUrl)
        putIfAbsent("supports_ingest", true)
        putIfAbsent("supports_stats", false)
        putIfAbsent("supported_workflows", emptyList<String>())
      }.toTelemetryProxyCapabilities().also(::validateIngestCapabilities)
    } catch (error: HttpFailureException) {
      if (error.statusCode == HTTP_NOT_FOUND || error.statusCode == HTTP_METHOD_NOT_ALLOWED) {
        defaultProxyCapabilities(settings.proxyUrl, capabilitiesUrl).toTelemetryProxyCapabilities()
      } else {
        throw IllegalArgumentException(
          error.message ?: "Telemetry proxy capabilities request failed.",
          error,
        )
      }
    }
  }

  override fun fetchRemoteStats(settings: TelemetrySettings, request: RemoteStatsRequest): TelemetryRemoteStatsResult {
    validateRemoteStatsRequest(request)
    require(settings.proxyUrl.isNotBlank()) {
      "Telemetry relay URL is not configured."
    }
    val (resolvedDateFrom, resolvedDateTo) =
      parseRemoteStatsWindow(
        request.since,
        request.dateFrom,
        request.dateTo,
        LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC),
      )
    val capabilities = fetchProxyCapabilities(settings)
    validateRemoteStatsCapabilities(
      request = request,
      settings = settings,
      capabilities = capabilities,
    )
    val statsUrl = settings.proxyUrl.trimEnd('/') + "/stats"
    val payload =
      requestJson(
        url = statsUrl,
        payload =
        RemoteStatsQueryPayload(
          workflow = request.workflow,
          dateFrom = resolvedDateFrom,
          dateTo = resolvedDateTo,
          groupBy = request.groupBy,
        ).toPayload(),
        errorContext = "Remote telemetry stats request",
        headers = proxyAuthHeaders(resolvedEnvironment.environment),
        requester = resolvedRequester,
      ).toMutableMap()
    val responseCapabilitiesPresent = payload.containsKey("capabilities")
    payload.putIfAbsent("workflow", request.workflow)
    payload.putIfAbsent("date_from", resolvedDateFrom)
    payload.putIfAbsent("date_to", resolvedDateTo)
    payload.putIfAbsent("source", "remote_proxy")
    payload.putIfAbsent("stats_url", statsUrl)
    if (!payload.containsKey("capabilities")) {
      payload["capabilities"] = capabilities
    }
    if (request.groupBy.isNotBlank()) {
      payload.putIfAbsent("group_by", request.groupBy)
    }
    return payload.toTelemetryRemoteStatsResult(
      capabilities = capabilities,
      preserveResponseCapabilities = responseCapabilitiesPresent,
    )
  }
}

private fun requestJson(
  url: String,
  payload: Map<String, Any?>,
  errorContext: String,
  headers: Map<String, String>,
  requester: RemoteTransportPort,
): Map<String, Any?> {
  val response =
    requester.execute(
      "POST",
      url,
      JsonCodec.mapToJsonString(payload),
      defaultJsonHeaders() + headers,
    )
  ensureSuccessfulResponse(response, errorContext)
  return decodeJsonObject(response.body, errorContext)
}

private fun requestJsonGet(
  url: String,
  errorContext: String,
  headers: Map<String, String>,
  requester: RemoteTransportPort,
): Map<String, Any?> {
  val response =
    requester.execute(
      "GET",
      url,
      null,
      mapOf("User-Agent" to "skill-bill-telemetry/1.0") + headers,
    )
  ensureSuccessfulResponse(response, errorContext)
  return decodeJsonObject(response.body, errorContext)
}

private fun ensureSuccessfulResponse(response: RemoteTransportResponse, errorContext: String) {
  if (response.statusCode !in HTTP_OK_MIN..HTTP_OK_MAX) {
    throw HttpFailureException(
      response.statusCode,
      httpFailureMessage(response, errorContext),
    )
  }
}

private fun decodeJsonObject(body: String, errorContext: String): Map<String, Any?> {
  if (body.isBlank()) {
    return emptyMap()
  }
  val decoded =
    JsonCodec.parseObjectOrNull(body)
      ?: throw IllegalArgumentException("$errorContext returned invalid JSON.")
  return JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(decoded))
    ?: throw IllegalArgumentException(
      "$errorContext returned a non-object JSON payload.",
    )
}

private fun deliveryDetail(response: RemoteTransportResponse): String {
  val body = response.body.trim().take(DELIVERY_DETAIL_MAX_LENGTH)
  return if (body.isEmpty()) {
    "HTTP ${response.statusCode}"
  } else {
    "HTTP ${response.statusCode}: $body"
  }
}

private fun httpFailureMessage(response: RemoteTransportResponse, errorContext: String): String =
  if (response.body.isBlank()) {
    "$errorContext failed with HTTP ${response.statusCode}."
  } else {
    "$errorContext failed with HTTP ${response.statusCode}. ${response.body.trim()}"
  }

private fun proxyAuthHeaders(environment: Map<String, String>): Map<String, String> =
  environment[TELEMETRY_PROXY_STATS_TOKEN_ENVIRONMENT_KEY]
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.let { mapOf("Authorization" to "Bearer $it") }
    ?: emptyMap()

private fun defaultJsonHeaders(): Map<String, String> = mapOf(
  "Content-Type" to "application/json",
  "User-Agent" to "skill-bill-telemetry/1.0",
)

private fun EnvironmentContext.withProcessDefaults(): EnvironmentContext {
  val withUserHome =
    if (userHome == EnvironmentContext.UnspecifiedUserHome) {
      copy(userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize())
    } else {
      copy(userHome = userHome.toAbsolutePath().normalize())
    }
  return if (withUserHome.environment === EnvironmentContext.UnspecifiedEnvironment) {
    withUserHome.copy(environment = System.getenv())
  } else {
    withUserHome
  }
}

private const val HTTP_OK_MIN: Int = 200
private const val HTTP_OK_MAX: Int = 299
private const val DELIVERY_DETAIL_MAX_LENGTH: Int = 300

private class HttpFailureException(
  val statusCode: Int,
  message: String,
) : IllegalArgumentException(message)
