package skillbill.di.core
import me.tatarka.inject.annotations.Provides
import skillbill.contracts.time.JvmSystemClock
import skillbill.infrastructure.host.JdkBoundedWorkFanOutPort
import skillbill.infrastructure.host.JdkDaemonThreadPort
import skillbill.infrastructure.host.JdkIdentifierGeneratorPort
import skillbill.infrastructure.host.JdkRuntimeDiagnostics
import skillbill.infrastructure.host.JdkRuntimeTimingPort
import skillbill.infrastructure.host.JdkShutdownHookPort
import skillbill.infrastructure.host.jvm.JdkHostPlatformPort
import skillbill.model.OptionalCallbacks
import skillbill.ports.concurrency.BoundedWorkFanOutPort
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.process.DaemonThreadPort
import skillbill.ports.process.IdentifierGeneratorPort
import skillbill.ports.process.ShutdownHookPort
import skillbill.ports.system.HostPlatformPort
import skillbill.ports.time.RuntimeTimingPort
import java.time.Clock

internal interface RuntimeDiagnosticsProvides {
  @Provides @JvmSynthetic
  fun runtimeDiagnostics(adapter: JdkRuntimeDiagnostics): RuntimeDiagnostics = adapter

  @Provides @JvmSynthetic
  fun runtimeTimingPort(callbacks: OptionalCallbacks, adapter: JdkRuntimeTimingPort): RuntimeTimingPort =
    callbacks.runtimeTimingPort ?: adapter

  @Provides @JvmSynthetic
  fun shutdownHookPort(adapter: JdkShutdownHookPort): ShutdownHookPort = adapter

  @Provides @JvmSynthetic
  fun daemonThreadPort(adapter: JdkDaemonThreadPort): DaemonThreadPort = adapter

  @Provides @JvmSynthetic
  fun identifierGeneratorPort(adapter: JdkIdentifierGeneratorPort): IdentifierGeneratorPort = adapter

  @Provides @JvmSynthetic
  fun boundedWorkFanOutPort(adapter: JdkBoundedWorkFanOutPort): BoundedWorkFanOutPort = adapter

  @Provides @JvmSynthetic
  fun hostPlatformPort(callbacks: OptionalCallbacks): HostPlatformPort =
    callbacks.hostPlatformPort ?: JdkHostPlatformPort

  @Provides @JvmSynthetic
  fun runtimeClock(): Clock = JvmSystemClock
}
