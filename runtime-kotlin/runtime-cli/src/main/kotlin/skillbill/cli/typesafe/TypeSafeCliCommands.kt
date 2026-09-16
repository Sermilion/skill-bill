package skillbill.cli.typesafe

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import me.tatarka.inject.annotations.Inject
import skillbill.application.typesafe.SystemOneService
import skillbill.cli.kernel.CliRunState
import skillbill.cli.kernel.DocumentedCliCommand
import skillbill.cli.kernel.formatOption
import skillbill.config.model.TypeSafeSettingsPatch
import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.contracts.typesafe.SystemOneWireKeys
import skillbill.error.SkillBillRuntimeException
import skillbill.ports.typesafe.model.SystemOneAnswer
import skillbill.ports.typesafe.model.SystemOneChoiceAnswer
import skillbill.ports.typesafe.model.SystemOneConfiguration
import skillbill.ports.typesafe.model.SystemOneEvaluateResult
import skillbill.ports.typesafe.model.SystemOneNoulAnswer
import skillbill.ports.typesafe.model.SystemOneScoreAnswer

@Inject
class TypeSafeTopLevelCommand(
  statusCommand: TypeSafeStatusCommand,
  configureCommand: TypeSafeConfigureCommand,
  probeCommand: TypeSafeProbeCommand,
) : DocumentedCliCommand("typesafe", "Experimental TypeSafe System One client (off until enabled in config).") {
  init {
    subcommands(statusCommand, configureCommand, probeCommand)
  }

  override fun run() = Unit
}

@Inject
class TypeSafeStatusCommand(
  private val service: SystemOneService,
  private val state: CliRunState,
) : DocumentedCliCommand("status", "Show TypeSafe enablement and whether an API key is stored.") {
  private val format by formatOption()

  override fun run() {
    completeConfiguration(service.configuration())
  }

  private fun completeConfiguration(configuration: SystemOneConfiguration) {
    val payload = configuration.toPayload()
    if (format.wireName == "json") {
      state.complete(payload, format, exitCode = 0)
    } else {
      state.completeText(configuration.toText(), payload, exitCode = 0)
    }
  }
}

@Inject
class TypeSafeConfigureCommand(
  private val service: SystemOneService,
  private val state: CliRunState,
) : DocumentedCliCommand(
  "configure",
  "Store a TypeSafe API key and opt into the experimental client in machine config.",
) {
  private val apiKey by option("--api-key", help = "TypeSafe API key written to machine config.json.")
  private val baseUrl by option("--base-url", help = "Optional TypeSafe API root URL.")
  private val model by option("--model", help = "Optional default System One model.")
  private val enable by option("--enable", help = "Turn the experimental TypeSafe client on.").flag()
  private val disable by option("--disable", help = "Turn the experimental TypeSafe client off.").flag()
  private val format by formatOption()

  override fun run() {
    if (enable && disable) {
      fail("Pass only one of --enable or --disable.")
      return
    }
    val patch = TypeSafeSettingsPatch(
      enabled = when {
        enable -> true
        disable -> false
        else -> null
      },
      apiKey = apiKey?.trim()?.takeIf(String::isNotBlank),
      baseUrl = baseUrl?.trim()?.takeIf(String::isNotBlank),
      defaultModel = model?.trim()?.takeIf(String::isNotBlank),
    )
    if (patch.isEmpty()) {
      fail("Provide --api-key, --enable, --disable, --base-url, or --model.")
      return
    }
    try {
      val configuration = service.configure(patch)
      val payload = configuration.toPayload()
      if (format.wireName == "json") {
        state.complete(payload, format, exitCode = 0)
      } else {
        state.completeText(configuration.toText(), payload, exitCode = 0)
      }
    } catch (error: SkillBillRuntimeException) {
      fail(error.message.orEmpty())
    }
  }

  private fun fail(message: String) {
    if (format.wireName == "json") {
      state.complete(mapOf("error" to message), format, exitCode = 1)
    } else {
      state.completeText(message, mapOf("error" to message), exitCode = 1)
    }
  }
}

@Inject
class TypeSafeProbeCommand(
  private val service: SystemOneService,
  private val state: CliRunState,
) : DocumentedCliCommand("probe", "Call TypeSafe with a built-in connectivity noul (requires enablement).") {
  private val stateText by argument(help = "State text to evaluate")
  private val format by formatOption()

  override fun run() {
    try {
      val result = service.probeConnectivity(stateText)
      val payload = result.toPayload()
      if (format.wireName == "json") {
        state.complete(payload, format, exitCode = 0)
      } else {
        state.completeText(result.toText(), payload, exitCode = 0)
      }
    } catch (error: SkillBillRuntimeException) {
      val message = error.message.orEmpty()
      if (format.wireName == "json") {
        state.complete(mapOf("error" to message), format, exitCode = 1)
      } else {
        state.completeText(message, mapOf("error" to message), exitCode = 1)
      }
    }
  }
}

private fun SystemOneConfiguration.toPayload(): Map<String, Any?> = linkedMapOf(
  SystemOneConfigPayloadKeys.ENABLED to enabled,
  SystemOneConfigPayloadKeys.API_KEY_CONFIGURED to apiKeyConfigured,
  SystemOneConfigPayloadKeys.BASE_URL to baseUrl,
  SystemOneConfigPayloadKeys.DEFAULT_MODEL to defaultModel,
)

private fun SystemOneConfiguration.toText(): String = buildString {
  appendLine("typesafe_enabled: $enabled")
  appendLine("api_key_configured: $apiKeyConfigured")
  appendLine("base_url: $baseUrl")
  appendLine("default_model: $defaultModel")
}

private fun SystemOneEvaluateResult.toPayload(): Map<String, Any?> = linkedMapOf(
  SystemOneWireKeys.MODEL to model,
  SystemOneWireKeys.ANSWERS to answers.mapValues { (_, answer) -> answer.toPayload() },
  SystemOneWireKeys.USAGE to
    usage?.let {
      mapOf(
        SystemOneWireKeys.INPUT_TOKENS to it.inputTokens,
        SystemOneWireKeys.OUTPUT_TOKENS to it.outputTokens,
      )
    },
)

private fun SystemOneEvaluateResult.toText(): String = buildString {
  appendLine("model: $model")
  usage?.let { appendLine("usage: input_tokens=${it.inputTokens} output_tokens=${it.outputTokens}") }
  answers.forEach { (questionId, answer) ->
    appendLine("$questionId: ${answer.toText()}")
  }
}

private fun SystemOneAnswer.toPayload(): Map<String, Any?> = when (this) {
  is SystemOneNoulAnswer ->
    mapOf(
      SystemOneWireKeys.TYPE to answerType.wireValue,
      SystemOneWireKeys.NOUL to probabilityYes,
    )
  is SystemOneChoiceAnswer ->
    mapOf(
      SystemOneWireKeys.TYPE to answerType.wireValue,
      SystemOneWireKeys.CHOICE to choice,
      SystemOneWireKeys.PROBABILITIES to probabilities,
      SystemOneWireKeys.CONFIDENCE to confidence,
    )
  is SystemOneScoreAnswer ->
    mapOf(
      SystemOneWireKeys.TYPE to answerType.wireValue,
      SystemOneWireKeys.SCORE to score,
      SystemOneWireKeys.LEGEND to legend,
      SystemOneWireKeys.PROBABILITIES to probabilities,
      SystemOneWireKeys.CONFIDENCE to confidence,
    )
}

private fun SystemOneAnswer.toText(): String = when (this) {
  is SystemOneNoulAnswer -> "noul=$probabilityYes"
  is SystemOneChoiceAnswer -> "choice=$choice confidence=$confidence"
  is SystemOneScoreAnswer -> "score=$score confidence=$confidence"
}
