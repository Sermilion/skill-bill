package skillbill.di.install
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.http.HttpInstallerScriptFetchAdapter
import skillbill.infrastructure.launcher.InstallerProcessAdapter
import skillbill.model.OptionalCallbacks
import skillbill.ports.process.InstallerProcessPort
import skillbill.ports.process.InstallerScriptFetchPort

internal interface RuntimeInstallerProvides {
  @Provides @JvmSynthetic
  fun installerProcessPort(
    callbacks: OptionalCallbacks,
    adapter: InstallerProcessAdapter,
  ): InstallerProcessPort = callbacks.installerProcessPort ?: adapter

  @Provides @JvmSynthetic
  fun installerScriptFetchPort(
    callbacks: OptionalCallbacks,
    adapter: HttpInstallerScriptFetchAdapter,
  ): InstallerScriptFetchPort = callbacks.installerScriptFetchPort ?: adapter
}
