package skillbill.infrastructure.http

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.typesafe.SystemOneDefaults
import skillbill.contracts.typesafe.SystemOneHttpHeaderKeys
import skillbill.error.SystemOneHttpResponseError
import skillbill.model.TransportContext
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.typesafe.SystemOneEvaluationPort
import skillbill.ports.typesafe.model.SystemOneCredentials
import skillbill.ports.typesafe.model.SystemOneEvaluateRequest
import skillbill.ports.typesafe.model.SystemOneEvaluateResult
import java.io.IOException

@Inject
class HttpSystemOneEvaluationClient(
  private val transportContext: TransportContext,
) : SystemOneEvaluationPort {
  private val requester: RemoteTransportPort =
    transportContext.requester
      ?: JdkHttpRemoteTransport.create(transportContext.connectTimeout, transportContext.requestTimeout)

  override fun evaluate(
    credentials: SystemOneCredentials,
    request: SystemOneEvaluateRequest,
  ): SystemOneEvaluateResult {
    val url = credentials.baseUrl.trimEnd('/') + SystemOneDefaults.EVALUATE_PATH
    val model = request.model?.takeIf(String::isNotBlank) ?: credentials.defaultModel
    val bodyJson = systemOneRequestJson(request, model)
    val response =
      try {
        requester.execute(
          method = "POST",
          url = url,
          bodyJson = bodyJson,
          headers = systemOneRequestHeaders(credentials.apiKey),
        )
      } catch (error: IOException) {
        throw SystemOneHttpResponseError(
          statusCode = 0,
          detail = "network failure: ${error.message ?: error::class.simpleName}",
        )
      }
    if (response.statusCode !in HTTP_SUCCESS_RANGE) {
      throw SystemOneHttpResponseError(
        statusCode = response.statusCode,
        detail = "response body omitted",
      )
    }
    return parseSystemOneEvaluateResult(response.body)
  }
}

private fun systemOneRequestHeaders(apiKey: String): Map<String, String> = mapOf(
  SystemOneHttpHeaderKeys.AUTHORIZATION to "Bearer $apiKey",
  SystemOneHttpHeaderKeys.CONTENT_TYPE to "application/json",
  SystemOneHttpHeaderKeys.ACCEPT to "application/json",
  SystemOneHttpHeaderKeys.USER_AGENT to SYSTEM_ONE_USER_AGENT,
)

private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
private val HTTP_SUCCESS_RANGE = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
private const val SYSTEM_ONE_USER_AGENT = "skill-bill-typesafe/1.0"
