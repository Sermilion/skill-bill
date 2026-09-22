package skillbill.ports.install.platformpack

import skillbill.ports.install.platformpack.model.ExternalPlatformPackPathResolveRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackPathResolveResult
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceConfigRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceConfigResult
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceRegistrationRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceUnregisterRequest

interface ExternalPlatformPackSourceConfigPort {
  fun readExternalPlatformPackSources(
    request: ExternalPlatformPackSourceConfigRequest,
  ): ExternalPlatformPackSourceConfigResult

  fun registerExternalPlatformPackSource(
    request: ExternalPlatformPackSourceRegistrationRequest,
  ): ExternalPlatformPackSourceConfigResult

  fun unregisterExternalPlatformPackSource(
    request: ExternalPlatformPackSourceUnregisterRequest,
  ): ExternalPlatformPackSourceConfigResult

  fun resolveExternalPlatformPackPath(
    request: ExternalPlatformPackPathResolveRequest,
  ): ExternalPlatformPackPathResolveResult
}
