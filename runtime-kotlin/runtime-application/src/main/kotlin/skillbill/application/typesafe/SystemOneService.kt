package skillbill.application.typesafe

import me.tatarka.inject.annotations.Inject
import skillbill.config.model.TypeSafeSettings
import skillbill.config.model.TypeSafeSettingsParse
import skillbill.config.model.TypeSafeSettingsPatch
import skillbill.config.model.parseTypeSafeSettings
import skillbill.config.model.withTypeSafeSettings
import skillbill.contracts.experiment.config.ExperimentConfigPayloadKeys
import skillbill.contracts.experiment.config.ExperimentNames
import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.contracts.typesafe.SystemOneEnvironmentKeys
import skillbill.error.SystemOneApiKeyMissingError
import skillbill.error.SystemOneNotEnabledError
import skillbill.error.shellcontent.ExperimentConfigMalformedError
import skillbill.error.shellcontent.MalformedMachineConfigError
import skillbill.experiment.ExperimentAvailabilityResolver
import skillbill.experiment.model.ExperimentConfigParse
import skillbill.experiment.model.parseExperimentAvailabilityValue
import skillbill.experiment.model.withListedExperimentNames
import skillbill.model.EnvironmentContext
import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.ports.typesafe.SystemOneEvaluationPort
import skillbill.ports.typesafe.model.SystemOneConfiguration
import skillbill.ports.typesafe.model.SystemOneCredentials
import skillbill.ports.typesafe.model.SystemOneEvaluateRequest
import skillbill.ports.typesafe.model.SystemOneEvaluateResult
import skillbill.ports.typesafe.model.SystemOneNoulQuestion
import skillbill.telemetry.model.TelemetryConfigDocument

@Inject
class SystemOneService(
  private val environmentContext: EnvironmentContext,
  private val machineConfigStore: TelemetryConfigStore,
  private val evaluationPort: SystemOneEvaluationPort,
) {
  fun configuration(): SystemOneConfiguration {
    val snapshot = readSnapshot()
    return snapshot.settings.toConfiguration(snapshot.typeSafeEnabled)
  }

  fun configure(patch: TypeSafeSettingsPatch): SystemOneConfiguration {
    if (patch.isEmpty()) {
      return configuration()
    }
    val current = readSnapshot()
    val baseDocument = current.document ?: machineConfigStore.ensure()
    var document = baseDocument.withTypeSafeSettings(patch)
    val merged = parseSettings(document.payload[SystemOneConfigPayloadKeys.ROOT])
    val enablement = patch.enabled
    if (enablement == true && resolvedApiKey(merged) == null) {
      throw SystemOneApiKeyMissingError()
    }
    if (enablement != null) {
      document = nextExperimentDocument(document, current.experimentsPresent, current.listedExperiments, enablement)
    }
    machineConfigStore.write(document)
    return merged.toConfiguration(
      ExperimentAvailabilityResolver.isExplicitlyListed(
        ExperimentNames.TYPESAFE,
        parseExperimentPolicy(document.payload),
      ),
    )
  }

  fun evaluate(request: SystemOneEvaluateRequest): SystemOneEvaluateResult {
    val snapshot = readSnapshot()
    if (!snapshot.typeSafeEnabled) {
      throw SystemOneNotEnabledError()
    }
    val apiKey = resolvedApiKey(snapshot.settings) ?: throw SystemOneApiKeyMissingError()
    return evaluationPort.evaluate(
      SystemOneCredentials(
        apiKey = apiKey,
        baseUrl = resolvedBaseUrl(snapshot.settings),
        defaultModel = resolvedModel(snapshot.settings),
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

  private fun readSnapshot(): TypeSafeSnapshot {
    val document = readDocument() ?: return TypeSafeSnapshot(
      document = null,
      settings = TypeSafeSettings.DEFAULT,
      typeSafeEnabled = false,
      listedExperiments = emptyList(),
      experimentsPresent = false,
    )
    val settings =
      if (!document.payload.containsKey(SystemOneConfigPayloadKeys.ROOT)) {
        TypeSafeSettings.DEFAULT
      } else {
        parseSettings(document.payload[SystemOneConfigPayloadKeys.ROOT])
      }
    val policy = parseExperimentPolicy(document.payload)
    return TypeSafeSnapshot(
      document = document,
      settings = settings,
      typeSafeEnabled = ExperimentAvailabilityResolver.isExplicitlyListed(ExperimentNames.TYPESAFE, policy),
      listedExperiments = ExperimentAvailabilityResolver.listedNames(policy),
      experimentsPresent = document.payload.containsKey(ExperimentConfigPayloadKeys.EXPERIMENTS),
    )
  }

  private fun readDocument(): TelemetryConfigDocument? {
    val configPath = machineConfigStore.configPath()
    return try {
      machineConfigStore.read()
    } catch (error: IllegalArgumentException) {
      throw MalformedMachineConfigError(
        path = configPath.toString(),
        key = "",
        value = "<document>",
        reason = "is not valid JSON.",
        cause = error,
      )
    }
  }

  private fun nextExperimentDocument(
    document: TelemetryConfigDocument,
    experimentsPresent: Boolean,
    listedExperiments: List<String>,
    enabled: Boolean,
  ): TelemetryConfigDocument {
    val nextNames =
      if (enabled) {
        if (ExperimentNames.TYPESAFE in listedExperiments) {
          listedExperiments
        } else {
          listedExperiments + ExperimentNames.TYPESAFE
        }
      } else {
        listedExperiments.filter { name -> name != ExperimentNames.TYPESAFE }
      }
    if (!enabled && !experimentsPresent) {
      return document
    }
    return document.withListedExperimentNames(nextNames.sorted())
  }

  private fun parseSettings(raw: Any?): TypeSafeSettings = when (val parsed = parseTypeSafeSettings(raw)) {
    is TypeSafeSettingsParse.Valid -> parsed.settings
    is TypeSafeSettingsParse.Invalid -> throw malformed(parsed)
  }

  private fun parseExperimentPolicy(payload: Map<String, Any?>) =
    if (!payload.containsKey(ExperimentConfigPayloadKeys.EXPERIMENTS)) {
      null
    } else {
      when (val parsed = parseExperimentAvailabilityValue(payload[ExperimentConfigPayloadKeys.EXPERIMENTS])) {
        is ExperimentConfigParse.Valid -> parsed.policy
        is ExperimentConfigParse.Invalid -> throw ExperimentConfigMalformedError(
          path = machineConfigStore.configPath().toString(),
          key = parsed.key,
          reason = parsed.reason,
        )
      }
    }

  private fun malformed(parsed: TypeSafeSettingsParse.Invalid): MalformedMachineConfigError =
    MalformedMachineConfigError(
      path = machineConfigStore.configPath().toString(),
      key = parsed.keyPath,
      value = parsed.value,
      reason = parsed.reason,
    )

  private fun TypeSafeSettings.toConfiguration(enabled: Boolean): SystemOneConfiguration = SystemOneConfiguration(
    enabled = enabled,
    apiKeyConfigured = resolvedApiKey(this) != null,
    baseUrl = resolvedBaseUrl(this),
    defaultModel = resolvedModel(this),
  )

  private fun resolvedApiKey(settings: TypeSafeSettings): String? = settings.apiKey?.trim()?.takeIf(String::isNotBlank)
    ?: environmentContext.environment[SystemOneEnvironmentKeys.API_KEY]?.trim()?.takeIf(String::isNotBlank)

  private fun resolvedBaseUrl(settings: TypeSafeSettings): String = settings.baseUrl

  private fun resolvedModel(settings: TypeSafeSettings): String = settings.defaultModel

  private data class TypeSafeSnapshot(
    val document: TelemetryConfigDocument?,
    val settings: TypeSafeSettings,
    val typeSafeEnabled: Boolean,
    val listedExperiments: List<String>,
    val experimentsPresent: Boolean,
  )
}
