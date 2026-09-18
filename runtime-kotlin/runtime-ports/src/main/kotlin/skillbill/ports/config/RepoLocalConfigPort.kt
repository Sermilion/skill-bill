package skillbill.ports.config

import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import skillbill.ports.config.model.ReadRepoLocalConfigResult

/**
 * Domain-owned port for reading the repo-local `.skill-bill/config.yaml`. Application code
 * reaches the config only through this port; the `runtime-infra/host (`:runtime-infra:host`)` adapter owns all file IO.
 */
interface RepoLocalConfigPort {
  fun readRepoLocalConfig(request: ReadRepoLocalConfigRequest): ReadRepoLocalConfigResult
}
