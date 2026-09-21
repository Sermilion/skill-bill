package skillbill.infrastructure.host.experiment
import me.tatarka.inject.annotations.Inject
import skillbill.config.model.ExperimentAvailabilityPolicy
import skillbill.error.shellcontent.ExperimentConfigMalformedError
import skillbill.experiment.model.ExperimentConfigParse
import skillbill.experiment.model.parseExperimentAvailabilityValue
import skillbill.infrastructure.host.readTelemetryConfigFile
import skillbill.infrastructure.host.resolveTelemetryConfigPath
import skillbill.infrastructure.host.withProcessDefaults
import skillbill.model.EnvironmentContext
import skillbill.ports.experiment.config.MachineExperimentConfigStore
import java.nio.file.Files

@Inject
class FileMachineExperimentConfigStore(
  private val context: EnvironmentContext,
) : MachineExperimentConfigStore {
  private val resolvedContext = context.withProcessDefaults()

  override fun readExperimentsAvailability(): ExperimentAvailabilityPolicy? {
    val configPath = resolveTelemetryConfigPath(resolvedContext.environment, resolvedContext.userHome)
    if (!Files.exists(configPath)) return null
    val document = readTelemetryConfigFile(configPath) ?: return null
    if (!document.payload.containsKey(EXPERIMENTS_CONFIG_KEY)) return null
    val raw = document.payload[EXPERIMENTS_CONFIG_KEY]
    return when (val parsed = parseExperimentAvailabilityValue(raw)) {
      is ExperimentConfigParse.Valid -> parsed.policy
      is ExperimentConfigParse.Invalid -> throw ExperimentConfigMalformedError(
        path = configPath.toString(),
        key = parsed.key,
        reason = parsed.reason,
      )
    }
  }
}

internal const val EXPERIMENTS_CONFIG_KEY: String = "experiments"
