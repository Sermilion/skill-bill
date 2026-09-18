package skillbill.infrastructure.fs.install.plan

import skillbill.infrastructure.fs.jvm.JdkHostPlatformPort
import skillbill.infrastructure.fs.jvm.resolveEnvironmentMap
import skillbill.infrastructure.fs.jvm.resolveUserHome
import skillbill.ports.system.HostPlatformPort
import java.nio.file.Path

internal fun resolveInstallHome(home: Path?, hostPlatform: HostPlatformPort = JdkHostPlatformPort): Path =
  resolveUserHome(home, hostPlatform)

internal fun resolveInstallEnvironment(
  environment: Map<String, String>,
  hostPlatform: HostPlatformPort = JdkHostPlatformPort,
): Map<String, String> = resolveEnvironmentMap(environment, hostPlatform)
