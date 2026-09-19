package skillbill.workflow.taskruntime.phase
import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.model.handoff.envelope.SettlementEnvelopeRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProsePhaseOutputSynthesizerTest {
  @Test
  fun `implementation_receipt sibling becomes stuffed value`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "implement",
        "status": "completed",
        "summary": "Did the work.",
        "produced_outputs": {
          "implementation_receipt": {
            "projection_kind": "implementation_receipt",
            "completed_task_ids": ["task-1"]
          }
        }
      }
      """.trimIndent()

    val envelope = assertNotNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "implement"))
    val map = envelopeMap(envelope)
    val produced = assertNotNull(JsonCodec.anyToStringAnyMap(map["produced_outputs"]))
    val value = assertNotNull(produced["value"] as? String)
    assertTrue(value.contains("implementation_receipt") || value.contains("completed_task_ids"))
    assertEquals("completed", map["status"])
    assertEquals("implement", map["phase_id"])
    assertNull(produced["implementation_receipt"])
  }

  @Test
  fun `audit with explicit empty remaining list synthesizes satisfied verdict`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "audit",
        "status": "completed",
        "summary": "All good.",
        "produced_outputs": { "value": "[]" }
      }
      """.trimIndent()

    val envelope = assertNotNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "audit"))
    assertEquals("satisfied", envelopeMap(envelope)["verdict"])
  }

  @Test
  fun `audit without recoverable verdict rejects`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "audit",
        "status": "completed",
        "summary": "Checked criteria.",
        "produced_outputs": { "value": "gaps remain on AC-1" }
      }
      """.trimIndent()

    assertNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "audit"))
  }

  @Test
  fun `blank value rejects`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "plan",
        "status": "completed",
        "summary": "Empty.",
        "produced_outputs": { "value": "   " }
      }
      """.trimIndent()

    assertNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "plan"))
  }

  @Test
  fun `bare non-json prose rejects`() {
    assertNull(ProsePhaseOutputSynthesizer.trySynthesize("not a json object", "implement"))
  }

  @Test
  fun `wrong phase_id rejects instead of rewriting identity`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "plan",
        "status": "completed",
        "summary": "Wrong slot.",
        "produced_outputs": { "value": "plan prose" }
      }
      """.trimIndent()

    assertNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "preplan"))
  }

  @Test
  fun `unsupported status rejects instead of defaulting`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "plan",
        "status": "queued",
        "summary": "Bad status.",
        "produced_outputs": { "value": "plan prose" }
      }
      """.trimIndent()

    assertNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "plan"))
  }

  @Test
  fun `produced_outputs as a name-value list under a near-miss status recovers instead of blocking`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "implement",
        "status": "complete",
        "summary": "Applied every plan task.",
        "tests_executed": [],
        "produced_outputs": [ { "name": "implementation_receipt", "value": "implementation prose" } ]
      }
      """.trimIndent()

    val envelope = assertNotNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "implement"))
    val map = envelopeMap(envelope)
    val produced = assertNotNull(JsonCodec.anyToStringAnyMap(map["produced_outputs"]))
    assertEquals("implementation prose", produced["value"])
    assertEquals("completed", map["status"])
    assertNull(map["tests_executed"])
  }

  @Test
  fun `historical prose status aliases retain canonical output`() {
    listOf("complete" to "completed", "block" to "blocked", "fail" to "failed").forEach { (input, output) ->
      val raw = """
        {
          "phase_id": "implement",
          "status": "$input",
          "summary": "Applied the plan.",
          "produced_outputs": [ { "name": "implementation_receipt", "value": "implementation receipt" } ]
        }
      """.trimIndent()

      val envelope = assertNotNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "implement"))
      assertEquals(output, envelopeMap(envelope)["status"])
    }
  }

  @Test
  fun `audit with verdict field synthesizes`() {
    val raw =
      """
      {
        "contract_version": "0.6",
        "phase_id": "audit",
        "status": "completed",
        "summary": "All good.",
        "verdict": "satisfied",
        "produced_outputs": { "value": "all criteria met" }
      }
      """.trimIndent()

    val envelope = assertNotNull(ProsePhaseOutputSynthesizer.trySynthesize(raw, "audit"))
    assertEquals("satisfied", envelopeMap(envelope)["verdict"])
  }

  @Test
  fun `blocked audit external settlement omits verdict`() {
    val envelope = ProsePhaseOutputSynthesizer.envelopeFromSettlement(
      SettlementEnvelopeRequest(
        phaseId = "audit",
        status = "blocked",
        value = "Planning criterion list unreadable.",
        summary = "Audit blocked on external dependency.",
        failureDisposition = "needs_user_action",
      ),
    )
    val map = envelopeMap(envelope)
    assertEquals("blocked", map["status"])
    assertNull(map["verdict"])
    assertEquals("needs_user_action", map["failure_disposition"])
  }

  private fun envelopeMap(envelope: Any): Map<String, Any?> =
    JsonCodec.anyToStringAnyMap(envelope) ?: error("expected object envelope")
}
