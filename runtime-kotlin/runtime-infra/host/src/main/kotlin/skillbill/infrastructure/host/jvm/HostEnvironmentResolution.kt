package skillbill.infrastructure.host.jvm

import skillbill.ports.system.HostPlatformPort
import java.nio.file.Path

fun resolveUserHome(home: Path?, hostPlatform: HostPlatformPort = JdkHostPlatformPort): Path =
  (home ?: hostPlatform.resolveUserHome()).toAbsolutePath().normalize()

fun resolveEnvironmentMap(
  environment: Map<String, String>,
  hostPlatform: HostPlatformPort = JdkHostPlatformPort,
): Map<String, String> = environment.ifEmpty { hostPlatform.resolveEnvironment() }
