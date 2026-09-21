package skillbill.application.typesafe

import skillbill.config.model.TypeSafeSettingsPatch
import skillbill.contracts.experiment.config.ExperimentConfigPayloadKeys
import skillbill.contracts.experiment.config.ExperimentNames
import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.contracts.typesafe.SystemOneDefaults
import skillbill.contracts.typesafe.SystemOneEnvironmentKeys
import skillbill.error.SystemOneApiKeyMissingError
import skillbill.error.SystemOneNotEnabledError
import skillbill.model.EnvironmentContext
import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.ports.typesafe.SystemOneEvaluationPort
import skillbill.ports.typesafe.model.SystemOneCredentials
import skillbill.ports.typesafe.model.SystemOneEvaluateRequest
import skillbill.ports.typesafe.model.SystemOneEvaluateResult
import skillbill.ports.typesafe.model.SystemOneNoulAnswer
import skillbill.ports.typesafe.model.SystemOneNoulQuestion
import skillbill.telemetry.model.TelemetryConfigDocument
import skillbill.telemetry.model.TelemetryOpenDocument
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemOneServiceTest {
  @Test
  fun `client without typesafe experiment rejects evaluate and probe even when env api key is set`() {
    val port = RecordingPort()
    val service =
      service(
        environment = mapOf(SystemOneEnvironmentKeys.API_KEY to "env-secret"),
        payload = mapOf(SystemOneConfigPayloadKeys.ROOT to emptyMap<String, Any?>()),
        port = port,
      )
    val request = sampleRequest()
    assertFailsWith<SystemOneNotEnabledError> { service.evaluate(request) }
    assertFailsWith<SystemOneNotEnabledError> { service.probeConnectivity("hello") }
    assertEquals(0, port.calls.size)
  }

  @Test
  fun `absent typesafe config rejects evaluate and probe even when env api key is set`() {
    val port = RecordingPort()
    val service =
      service(
        environment = mapOf(SystemOneEnvironmentKeys.API_KEY to "env-secret"),
        payload = mapOf("install_id" to "test"),
        port = port,
      )
    assertFailsWith<SystemOneNotEnabledError> { service.evaluate(sampleRequest()) }
    assertFailsWith<SystemOneNotEnabledError> { service.probeConnectivity("hello") }
    assertEquals(0, port.calls.size)
  }

  @Test
  fun `other experiments without typesafe reject evaluate`() {
    val port = RecordingPort()
    val service =
      service(
        environment = mapOf(SystemOneEnvironmentKeys.API_KEY to "env-secret"),
        payload = mapOf(ExperimentConfigPayloadKeys.EXPERIMENTS to listOf("codegraph")),
        port = port,
      )
    assertFailsWith<SystemOneNotEnabledError> { service.evaluate(sampleRequest()) }
    assertEquals(0, port.calls.size)
  }

  @Test
  fun `listed typesafe experiment without api key rejects evaluate`() {
    val port = RecordingPort()
    val service =
      service(
        payload = mapOf(ExperimentConfigPayloadKeys.EXPERIMENTS to listOf(ExperimentNames.TYPESAFE)),
        port = port,
      )
    assertFailsWith<SystemOneApiKeyMissingError> { service.evaluate(sampleRequest()) }
    assertEquals(0, port.calls.size)
  }

  @Test
  fun `listed typesafe experiment with stored api key forwards evaluate to the port`() {
    val port = RecordingPort()
    val service =
      service(
        payload =
        mapOf(
          ExperimentConfigPayloadKeys.EXPERIMENTS to listOf(ExperimentNames.TYPESAFE),
          SystemOneConfigPayloadKeys.ROOT to mapOf(SystemOneConfigPayloadKeys.API_KEY to "stored-secret"),
        ),
        port = port,
      )
    val result = service.evaluate(sampleRequest())
    assertEquals(SystemOneDefaults.MODEL, result.model)
    assertEquals(1, port.calls.size)
    assertEquals("stored-secret", port.calls.single().credentials.apiKey)
    assertEquals("hello", port.calls.single().request.state)
  }

  @Test
  fun `listed typesafe experiment uses env api key when config key is absent`() {
    val port = RecordingPort()
    val service =
      service(
        environment = mapOf(SystemOneEnvironmentKeys.API_KEY to "env-secret"),
        payload = mapOf(ExperimentConfigPayloadKeys.EXPERIMENTS to listOf(ExperimentNames.TYPESAFE)),
        port = port,
      )
    service.evaluate(sampleRequest())
    assertEquals("env-secret", port.calls.single().credentials.apiKey)
  }

  @Test
  fun `configure enable without api key throws and does not persist typesafe on experiments`() {
    val store = FakeTypeSafeConfigStore(
      mapOf(
        SystemOneConfigPayloadKeys.ROOT to emptyMap<String, Any?>(),
      ),
    )
    val service =
      SystemOneService(
        EnvironmentContext(environment = emptyMap()),
        store,
        RecordingPort(),
      )
    assertFailsWith<SystemOneApiKeyMissingError> {
      service.configure(TypeSafeSettingsPatch(enabled = true))
    }
    assertFalse(store.document.payload.containsKey(ExperimentConfigPayloadKeys.EXPERIMENTS))
  }

  @Test
  fun `configure enable lists typesafe without dropping other experiments`() {
    val store = FakeTypeSafeConfigStore(
      mapOf(
        ExperimentConfigPayloadKeys.EXPERIMENTS to listOf("codegraph"),
        SystemOneConfigPayloadKeys.ROOT to mapOf(SystemOneConfigPayloadKeys.API_KEY to "stored-secret"),
      ),
    )
    val service =
      SystemOneService(
        EnvironmentContext(environment = emptyMap()),
        store,
        RecordingPort(),
      )

    val configuration = service.configure(TypeSafeSettingsPatch(enabled = true))

    assertTrue(configuration.enabled)
    assertEquals(
      listOf("codegraph", ExperimentNames.TYPESAFE),
      store.document.payload[ExperimentConfigPayloadKeys.EXPERIMENTS],
    )
  }

  @Test
  fun `configure enable persists a supplied api key and lists typesafe`() {
    val store = FakeTypeSafeConfigStore(mapOf("install_id" to "test"))
    val service =
      SystemOneService(
        EnvironmentContext(environment = emptyMap()),
        store,
        RecordingPort(),
      )

    val configuration =
      service.configure(
        TypeSafeSettingsPatch(enabled = true, apiKey = "supplied-secret"),
      )

    assertTrue(configuration.enabled)
    assertTrue(configuration.apiKeyConfigured)
    assertEquals(listOf(ExperimentNames.TYPESAFE), store.document.payload[ExperimentConfigPayloadKeys.EXPERIMENTS])
    val typesafe = store.document.payload[SystemOneConfigPayloadKeys.ROOT] as Map<*, *>
    assertEquals("supplied-secret", typesafe[SystemOneConfigPayloadKeys.API_KEY])
    assertFalse(typesafe.containsKey(SystemOneConfigPayloadKeys.ENABLED))
  }

  @Test
  fun `configuration reports api key presence without echoing secrets`() {
    val service =
      service(
        payload =
        mapOf(
          ExperimentConfigPayloadKeys.EXPERIMENTS to listOf(ExperimentNames.TYPESAFE),
          SystemOneConfigPayloadKeys.ROOT to mapOf(SystemOneConfigPayloadKeys.API_KEY to "stored-secret"),
        ),
      )
    val configuration = service.configuration()
    assertTrue(configuration.enabled)
    assertTrue(configuration.apiKeyConfigured)
    assertEquals(SystemOneDefaults.BASE_URL, configuration.baseUrl)
    assertEquals(SystemOneDefaults.MODEL, configuration.defaultModel)
  }

  private fun service(
    environment: Map<String, String> = emptyMap(),
    payload: Map<String, Any?>,
    port: RecordingPort = RecordingPort(),
  ): SystemOneService = SystemOneService(
    EnvironmentContext(environment = environment),
    FakeTypeSafeConfigStore(payload),
    port,
  )

  private fun sampleRequest(): SystemOneEvaluateRequest = SystemOneEvaluateRequest(
    state = "hello",
    questions = mapOf("ping" to SystemOneNoulQuestion("Is this a greeting?")),
  )

  private class RecordingPort : SystemOneEvaluationPort {
    val calls = mutableListOf<Call>()

    override fun evaluate(
      credentials: SystemOneCredentials,
      request: SystemOneEvaluateRequest,
    ): SystemOneEvaluateResult {
      calls += Call(credentials, request)
      return SystemOneEvaluateResult(
        model = credentials.defaultModel,
        answers = mapOf("ping" to SystemOneNoulAnswer(0.9)),
        usage = null,
      )
    }

    data class Call(
      val credentials: SystemOneCredentials,
      val request: SystemOneEvaluateRequest,
    )
  }

  private class FakeTypeSafeConfigStore(
    initialPayload: Map<String, Any?>,
  ) : TelemetryConfigStore {
    var document: TelemetryConfigDocument =
      TelemetryConfigDocument(TelemetryOpenDocument.from(initialPayload))

    override fun stateDir(): Path = Path.of("/fake")

    override fun configPath(): Path = Path.of("/fake/config.json")

    override fun read(): TelemetryConfigDocument = document

    override fun ensure(): TelemetryConfigDocument = document

    override fun write(document: TelemetryConfigDocument) {
      this.document = document
    }
  }
}
