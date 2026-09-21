package skillbill.di.experiment

import me.tatarka.inject.annotations.Provides
import skillbill.engine.experiment.navigation.BoundedReadOnlyExperimentNavigationSessionRunner
import skillbill.engine.experiment.telemetry.ExperimentTelemetryOutboxRecorder
import skillbill.engine.experiment.telemetry.ExperimentTelemetryOutboxSink
import skillbill.engine.experiment.telemetry.ExperimentTelemetryRecorder
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRunnerPort
import skillbill.ports.telemetry.transport.TelemetryConfigStore

internal interface RuntimeExperimentTelemetryProvides {
  @Provides @JvmSynthetic
  fun experimentTelemetryOutboxSink(database: DatabaseSessionFactory): ExperimentTelemetryOutboxSink =
    ExperimentTelemetryOutboxSink { eventName, payloadJson ->
      database.transaction { unitOfWork ->
        unitOfWork.telemetryOutbox.enqueue(eventName, payloadJson)
      }
    }

  @Provides @JvmSynthetic
  fun experimentTelemetryOutboxRecorder(
    configStore: TelemetryConfigStore,
    sink: ExperimentTelemetryOutboxSink,
  ): ExperimentTelemetryOutboxRecorder = ExperimentTelemetryOutboxRecorder(configStore, sink)

  @Provides @JvmSynthetic
  fun experimentTelemetryRecorder(recorder: ExperimentTelemetryOutboxRecorder): ExperimentTelemetryRecorder = recorder

  @Provides @JvmSynthetic
  fun experimentNavigationSessionRunner(): ExperimentNavigationSessionRunnerPort =
    BoundedReadOnlyExperimentNavigationSessionRunner { _ ->
      throw ExperimentIsolationCapabilityRefusalError(
        "No live navigation model provider is configured for this runtime.",
      )
    }
}
