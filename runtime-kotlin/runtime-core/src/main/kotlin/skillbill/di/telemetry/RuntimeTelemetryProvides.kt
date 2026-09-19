package skillbill.di.telemetry
import me.tatarka.inject.annotations.Provides
import skillbill.application.telemetry.lifecycle.GoalLifecycleTelemetryEmitter
import skillbill.application.telemetry.lifecycle.LifecycleTelemetryService
import skillbill.application.telemetry.service.TelemetryLevelMutationService
import skillbill.application.telemetry.settings.DefaultTelemetrySettingsProvider
import skillbill.di.core.SkillBillVersion
import skillbill.di.core.VALUE
import skillbill.infrastructure.host.FileTelemetryConfigStore
import skillbill.infrastructure.http.HttpTelemetryClient
import skillbill.ports.telemetry.transport.TelemetryClient
import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.ports.telemetry.transport.TelemetryLevelMutator
import skillbill.ports.telemetry.transport.TelemetrySettingsProvider

internal interface RuntimeTelemetryProvides {
  @Provides @JvmSynthetic
  fun telemetryConfigStore(store: FileTelemetryConfigStore): TelemetryConfigStore = store

  @Provides @JvmSynthetic
  fun telemetrySettingsProvider(provider: DefaultTelemetrySettingsProvider): TelemetrySettingsProvider = provider

  @Provides @JvmSynthetic
  fun telemetryClient(client: HttpTelemetryClient): TelemetryClient = client

  @Provides @JvmSynthetic
  fun telemetryLevelMutator(service: TelemetryLevelMutationService): TelemetryLevelMutator = service

  @Provides @JvmSynthetic
  fun goalLifecycleTelemetryEmitter(service: LifecycleTelemetryService): GoalLifecycleTelemetryEmitter = service

  @Provides @JvmSynthetic
  fun skillBillVersion(): String = SkillBillVersion.VALUE
}
