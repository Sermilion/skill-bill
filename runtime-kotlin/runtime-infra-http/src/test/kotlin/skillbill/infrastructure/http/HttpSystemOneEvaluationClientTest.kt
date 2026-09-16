package skillbill.infrastructure.http

import skillbill.contracts.typesafe.SystemOneHttpHeaderKeys
import skillbill.error.SystemOneHttpResponseError
import skillbill.error.SystemOneMalformedResponseError
import skillbill.model.TransportContext
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.telemetry.model.RemoteTransportResponse
import skillbill.ports.typesafe.model.SystemOneChoiceAnswer
import skillbill.ports.typesafe.model.SystemOneCredentials
import skillbill.ports.typesafe.model.SystemOneEvaluateRequest
import skillbill.ports.typesafe.model.SystemOneNoulAnswer
import skillbill.ports.typesafe.model.SystemOneNoulQuestion
import skillbill.ports.typesafe.model.SystemOneScoreAnswer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class HttpSystemOneEvaluationClientTest {
  @Test
  fun `evaluate posts to systemone and parses noul answers`() {
    val calls = mutableListOf<RecordedCall>()
    val client =
      HttpSystemOneEvaluationClient(
        TransportContext(
          requester =
          RemoteTransportPort { method, url, bodyJson, headers ->
            calls += RecordedCall(method, url, bodyJson, headers)
            RemoteTransportResponse(
              statusCode = 200,
              body =
              """
              {
                "model": "jev-latest",
                "answers": {
                  "connectivity": { "type": "noul", "noul": 0.88 }
                },
                "usage": { "input_tokens": 12, "output_tokens": 3 }
              }
              """.trimIndent(),
            )
          },
        ),
      )
    val result =
      client.evaluate(
        SystemOneCredentials(
          apiKey = "test-key",
          baseUrl = "https://api.typesafe.ai",
          defaultModel = "jev-latest",
        ),
        SystemOneEvaluateRequest(
          state = "hello",
          questions = mapOf("connectivity" to SystemOneNoulQuestion("Is this non-empty?")),
        ),
      )
    assertEquals("jev-latest", result.model)
    assertEquals(
      0.88,
      result.answers["connectivity"]!!.let { answer ->
        (answer as SystemOneNoulAnswer).probabilityYes
      },
    )
    assertEquals(12, result.usage?.inputTokens)
    val call = calls.single()
    assertEquals("POST", call.method)
    assertEquals("https://api.typesafe.ai/v1/systemone", call.url)
    assertEquals("Bearer test-key", call.headers[SystemOneHttpHeaderKeys.AUTHORIZATION])
    assertContains(call.bodyJson!!, "hello")
  }

  @Test
  fun `evaluate maps http failures to typed errors`() {
    val client =
      HttpSystemOneEvaluationClient(
        TransportContext(
          requester =
          RemoteTransportPort { _, _, _, _ ->
            RemoteTransportResponse(statusCode = 401, body = """{"error":"Bearer k"}""")
          },
        ),
      )
    val error =
      assertFailsWith<SystemOneHttpResponseError> {
        client.evaluate(sampleCredentials(), sampleRequest())
      }
    assertEquals(401, error.statusCode)
    assertFalse(error.message.orEmpty().contains("Bearer k"))
  }

  @Test
  fun `evaluate maps validation failures to typed http errors`() {
    val client =
      HttpSystemOneEvaluationClient(
        TransportContext(
          requester =
          RemoteTransportPort { _, _, _, _ ->
            RemoteTransportResponse(statusCode = 422, body = """{"error":"invalid request"}""")
          },
        ),
      )
    val error =
      assertFailsWith<SystemOneHttpResponseError> {
        client.evaluate(sampleCredentials(), sampleRequest())
      }
    assertEquals(422, error.statusCode)
  }

  @Test
  fun `evaluate rejects non-object success bodies`() {
    val client =
      HttpSystemOneEvaluationClient(
        TransportContext(
          requester =
          RemoteTransportPort { _, _, _, _ ->
            RemoteTransportResponse(statusCode = 200, body = "[]")
          },
        ),
      )
    assertFailsWith<SystemOneMalformedResponseError> {
      client.evaluate(sampleCredentials(), sampleRequest())
    }
  }

  @Test
  fun `evaluate parses choice and score answers in one response`() {
    val client =
      HttpSystemOneEvaluationClient(
        TransportContext(
          requester =
          RemoteTransportPort { _, _, _, _ ->
            RemoteTransportResponse(
              statusCode = 200,
              body =
              """
              {
                "model": "jev-latest",
                "answers": {
                  "tone": {
                    "type": "choice",
                    "choice": "formal",
                    "probabilities": { "formal": 0.7, "casual": 0.3 },
                    "confidence": 0.81
                  },
                  "quality": {
                    "type": "score",
                    "score": 4.0,
                    "legend": { "1": "poor", "5": "excellent" },
                    "probabilities": { "4.0": 0.6, "3.0": 0.4 },
                    "confidence": 0.77
                  }
                }
              }
              """.trimIndent(),
            )
          },
        ),
      )
    val result = client.evaluate(sampleCredentials(), sampleRequest())
    val tone = result.answers["tone"]
    val quality = result.answers["quality"]
    assertIs<SystemOneChoiceAnswer>(tone)
    assertEquals("formal", tone.choice)
    assertIs<SystemOneScoreAnswer>(quality)
    assertEquals(4.0, quality.score)
  }

  private fun sampleCredentials(): SystemOneCredentials =
    SystemOneCredentials(apiKey = "k", baseUrl = "https://api.typesafe.ai", defaultModel = "jev-latest")

  private fun sampleRequest(): SystemOneEvaluateRequest = SystemOneEvaluateRequest(
    state = "x",
    questions = mapOf("q" to SystemOneNoulQuestion("test?")),
  )

  private data class RecordedCall(
    val method: String,
    val url: String,
    val bodyJson: String?,
    val headers: Map<String, String>,
  )
}
