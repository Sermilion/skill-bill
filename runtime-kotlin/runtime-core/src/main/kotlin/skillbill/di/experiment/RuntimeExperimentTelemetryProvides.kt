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
  @Provides
  fun experimentTelemetryOutboxSink(database: DatabaseSessionFactory): ExperimentTelemetryOutboxSink =
    ExperimentTelemetryOutboxSink { event, payloadJson ->
      database.transaction { unitOfWork ->
        unitOfWork.telemetryOutbox.enqueue(event, payloadJson)
      }
    }

  @Provides
  fun experimentTelemetryOutboxRecorder(
    configStore: TelemetryConfigStore,
    sink: ExperimentTelemetryOutboxSink,
  ): ExperimentTelemetryOutboxRecorder = ExperimentTelemetryOutboxRecorder(configStore, sink)

  @Provides
  fun experimentTelemetryRecorder(recorder: ExperimentTelemetryOutboxRecorder): ExperimentTelemetryRecorder = recorder

  @Provides
  fun optionalExperimentTelemetryRecorder(): ExperimentTelemetryRecorder? = null

  @Provides
  fun experimentNavigationSessionRunner(): ExperimentNavigationSessionRunnerPort =
    BoundedReadOnlyExperimentNavigationSessionRunner { _ ->
      throw ExperimentIsolationCapabilityRefusalError(
        "No live navigation model provider is configured for this runtime.",
      )
    }
}
