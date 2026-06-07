package skillbill.application

import me.tatarka.inject.annotations.Inject
import skillbill.config.model.RepoLocalConfigResolution
import skillbill.config.model.SpecType
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import java.nio.file.Path

/**
 * Surfaces the single spec-source precedence point (`explicit arg > config spec_type >
 * built-in local`) over [RepoLocalConfigPort]. A malformed config propagates the typed
 * [skillbill.error.MalformedRepoLocalConfigError] unchanged so callers loud-fail instead
 * of silently defaulting.
 */
@Inject
class ConfigResolutionService(
  private val repoLocalConfigPort: RepoLocalConfigPort,
) {
  fun resolveSpecType(repoRoot: Path, explicit: SpecType?): SpecType {
    val config = repoLocalConfigPort.readRepoLocalConfig(ReadRepoLocalConfigRequest(repoRoot)).config
    return RepoLocalConfigResolution.resolve(explicit, config.specType, SpecType.LOCAL)
  }
}
