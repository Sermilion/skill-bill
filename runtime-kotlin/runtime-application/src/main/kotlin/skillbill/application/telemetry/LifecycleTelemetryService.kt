package skillbill.application.telemetry

import me.tatarka.inject.annotations.Inject
import skillbill.application.telemetry.model.FeatureTaskRuntimeFinishedRequest
import skillbill.application.telemetry.model.FeatureTaskRuntimeStartedRequest
import skillbill.application.telemetry.model.FeatureVerifyFinishedRequest
import skillbill.application.telemetry.model.FeatureVerifyStartedRequest
import skillbill.application.telemetry.model.PrDescriptionGeneratedRequest
import skillbill.application.telemetry.model.QualityCheckFinishedRequest
import skillbill.application.telemetry.model.QualityCheckStartedRequest
import skillbill.contracts.JsonPayloadContract
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.application.telemetry.settings.telemetrySettingsOrNull
import skillbill.ports.telemetry.TelemetrySettingsProvider

@Inject
class LifecycleTelemetryService(
  private val database: DatabaseSessionFactory,
  private val settingsProvider: TelemetrySettingsProvider,
) : GoalLifecycleTelemetryEmitter by LifecycleTelemetryGoalEmission(database, settingsProvider) {
  fun featureTaskRuntimeStarted(request: FeatureTaskRuntimeStartedRequest): JsonPayloadContract {
    val sessionId = request.sessionId.ifBlank { generateLifecycleSessionId("ftr") }
    return enabledStandaloneResult(settingsProvider, sessionId) { settings ->
      database.transaction { unitOfWork ->
        unitOfWork.lifecycleTelemetry.featureTaskRuntimeStarted(request.toRecord(sessionId), settings.level)
      }
    }
  }

  fun featureTaskRuntimeFinished(request: FeatureTaskRuntimeFinishedRequest): JsonPayloadContract =
    enabledStandaloneResult(settingsProvider, request.sessionId) { settings ->
      val reconciledRequest = request.reconcileBlockedRuntimeFields()
      database.transaction { unitOfWork ->
        unitOfWork.lifecycleTelemetry.featureTaskRuntimeFinished(reconciledRequest.toRecord(), settings.level)
      }
    }

  fun qualityCheckStarted(request: QualityCheckStartedRequest): JsonPayloadContract {
    val sessionId = generateLifecycleSessionId("qck")
    val normalizedRequest = request.normalizedLabels()
    return when {
      normalizedRequest.orchestrated -> orchestratedStartedSkippedPayload()
      else ->
        validateQualityCheckStarted(normalizedRequest)
          ?.let { lifecycleErrorPayload(sessionId, it) }
          ?: enabledStandaloneResult(settingsProvider, sessionId) { settings ->
            database.transaction { unitOfWork ->
              unitOfWork.lifecycleTelemetry.qualityCheckStarted(
                normalizedRequest.toRecord(sessionId),
                settings.level,
              )
            }
          }
    }
  }

  fun qualityCheckFinished(request: QualityCheckFinishedRequest): JsonPayloadContract {
    val normalizedRequest = request.normalizedLabels()
    return validateQualityCheckFinished(normalizedRequest)
      ?.let { lifecycleErrorPayload(normalizedRequest.sessionId, it) }
      ?: when {
        normalizedRequest.orchestrated ->
          normalizedRequest.orchestratedPayload(telemetryLevelOrAnonymous(settingsProvider))
        else ->
          enabledStandaloneResult(settingsProvider, normalizedRequest.sessionId) { settings ->
            database.transaction { unitOfWork ->
              unitOfWork.lifecycleTelemetry.qualityCheckFinished(
                normalizedRequest.toRecord(),
                settings.level,
              )
            }
          }
      }
  }

  fun featureVerifyStarted(request: FeatureVerifyStartedRequest): JsonPayloadContract {
    val sessionId = generateLifecycleSessionId("fvr")
    return when {
      request.orchestrated -> orchestratedStartedSkippedPayload()
      else ->
        enabledStandaloneResult(settingsProvider, sessionId) { settings ->
          database.transaction { unitOfWork ->
            unitOfWork.lifecycleTelemetry.featureVerifyStarted(request.toRecord(sessionId), settings.level)
          }
        }
    }
  }

  fun featureVerifyFinished(request: FeatureVerifyFinishedRequest): JsonPayloadContract =
    validateFeatureVerifyFinished(request)
      ?.let { lifecycleErrorPayload(request.sessionId, it) }
      ?: when {
        request.orchestrated -> request.orchestratedPayload(telemetryLevelOrAnonymous(settingsProvider))
        else ->
          enabledStandaloneResult(settingsProvider, request.sessionId) { settings ->
            database.transaction { unitOfWork ->
              unitOfWork.lifecycleTelemetry.featureVerifyFinished(request.toRecord(), settings.level)
            }
          }
      }

  fun prDescriptionGenerated(request: PrDescriptionGeneratedRequest): JsonPayloadContract {
    val sessionId = if (request.orchestrated) "" else generateLifecycleSessionId("prd")
    return when {
      request.orchestrated -> request.orchestratedPayload(telemetryLevelOrAnonymous(settingsProvider))
      else ->
        enabledStandaloneResult(settingsProvider, sessionId) { settings ->
          database.transaction { unitOfWork ->
            unitOfWork.lifecycleTelemetry.prDescriptionGenerated(request.toRecord(sessionId), settings.level)
          }
        }
    }
  }
}

internal fun telemetryLevelOrAnonymous(settingsProvider: TelemetrySettingsProvider): String =
  telemetrySettingsOrNull(settingsProvider)?.level ?: "anonymous"
