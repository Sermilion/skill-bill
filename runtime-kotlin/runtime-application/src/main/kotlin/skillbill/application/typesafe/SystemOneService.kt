package skillbill.application.typesafe

import me.tatarka.inject.annotations.Inject
import skillbill.config.model.TypeSafeSettings
import skillbill.config.model.TypeSafeSettingsParse
import skillbill.config.model.TypeSafeSettingsPatch
import skillbill.config.model.parseTypeSafeSettings
import skillbill.config.model.withTypeSafeSettings
import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.contracts.typesafe.SystemOneEnvironmentKeys
import skillbill.error.MalformedMachineConfigError
import skillbill.error.SystemOneApiKeyMissingError
import skillbill.error.SystemOneNotEnabledError
import skillbill.model.EnvironmentContext
import skillbill.ports.telemetry.TelemetryConfigStore
import skillbill.ports.typesafe.SystemOneEvaluationPort
import skillbill.ports.typesafe.model.SystemOneConfiguration
import skillbill.ports.typesafe.model.SystemOneCredentials
import skillbill.ports.typesafe.model.SystemOneEvaluateRequest
import skillbill.ports.typesafe.model.SystemOneEvaluateResult
import skillbill.ports.typesafe.model.SystemOneNoulQuestion

@Inject
class SystemOneService(
  private val environmentContext: EnvironmentContext,
  private val machineConfigStore: TelemetryConfigStore,
  private val evaluationPort: SystemOneEvaluationPort,
) {
  fun configuration(): SystemOneConfiguration {
    val settings = readSettings()
    return settings.toConfiguration()
  }

  fun configure(patch: TypeSafeSettingsPatch): SystemOneConfiguration {
    if (patch.isEmpty()) {
      return configuration()
    }
    val document = machineConfigStore.ensure().withTypeSafeSettings(patch)
    val merged = when (val parsed = parseTypeSafeSettings(document.payload[SystemOneConfigPayloadKeys.ROOT])) {
      is TypeSafeSettingsParse.Valid -> parsed.settings
      is TypeSafeSettingsParse.Invalid -> throw malformed(parsed)
    }
    if (merged.enabled && resolvedApiKey(merged) == null) {
      throw SystemOneApiKeyMissingError()
    }
    machineConfigStore.write(document)
    return merged.toConfiguration()
  }

  fun evaluate(request: SystemOneEvaluateRequest): SystemOneEvaluateResult {
    val settings = readSettings()
    if (!settings.enabled) {
      throw SystemOneNotEnabledError()
    }
    val apiKey = resolvedApiKey(settings) ?: throw SystemOneApiKeyMissingError()
    return evaluationPort.evaluate(
      SystemOneCredentials(
        apiKey = apiKey,
        baseUrl = resolvedBaseUrl(settings),
        defaultModel = resolvedModel(settings),
      ),
      request,
    )
  }

  fun probeConnectivity(state: String): SystemOneEvaluateResult = evaluate(
    SystemOneEvaluateRequest(
      state = state,
      questions =
      mapOf(
        "connectivity" to
          SystemOneNoulQuestion(
            instructions = "Is this text non-empty?",
          ),
      ),
    ),
  )

  private fun readSettings(): TypeSafeSettings {
    val configPath = machineConfigStore.configPath()
    val payload =
      try {
        machineConfigStore.read()?.payload
      } catch (error: IllegalArgumentException) {
        throw MalformedMachineConfigError(
          path = configPath.toString(),
          key = "",
          value = "<document>",
          reason = "is not valid JSON.",
          cause = error,
        )
      } ?: return TypeSafeSettings.DISABLED
    if (!payload.containsKey(SystemOneConfigPayloadKeys.ROOT)) {
      return TypeSafeSettings.DISABLED
    }
    return when (val parsed = parseTypeSafeSettings(payload[SystemOneConfigPayloadKeys.ROOT])) {
      is TypeSafeSettingsParse.Valid -> parsed.settings
      is TypeSafeSettingsParse.Invalid -> throw malformed(parsed)
    }
  }

  private fun malformed(parsed: TypeSafeSettingsParse.Invalid): MalformedMachineConfigError =
    MalformedMachineConfigError(
      path = machineConfigStore.configPath().toString(),
      key = parsed.keyPath,
      value = parsed.value,
      reason = parsed.reason,
    )

  private fun TypeSafeSettings.toConfiguration(): SystemOneConfiguration = SystemOneConfiguration(
    enabled = enabled,
    apiKeyConfigured = resolvedApiKey(this) != null,
    baseUrl = resolvedBaseUrl(this),
    defaultModel = resolvedModel(this),
  )

  private fun resolvedApiKey(settings: TypeSafeSettings): String? = settings.apiKey?.trim()?.takeIf(String::isNotBlank)
    ?: environmentContext.environment[SystemOneEnvironmentKeys.API_KEY]?.trim()?.takeIf(String::isNotBlank)

  private fun resolvedBaseUrl(settings: TypeSafeSettings): String = settings.baseUrl

  private fun resolvedModel(settings: TypeSafeSettings): String = settings.defaultModel
}
