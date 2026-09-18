package skillbill.infrastructure.fs.jvm

import skillbill.ports.system.HostPlatformPort
import java.nio.file.Path

internal fun resolveUserHome(home: Path?, hostPlatform: HostPlatformPort = JdkHostPlatformPort): Path =
  (home ?: hostPlatform.resolveUserHome()).toAbsolutePath().normalize()

internal fun resolveEnvironmentMap(
  environment: Map<String, String>,
  hostPlatform: HostPlatformPort = JdkHostPlatformPort,
): Map<String, String> = environment.ifEmpty { hostPlatform.resolveEnvironment() }
