package skillbill.workflow.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttemptLedgerWorkflowDecodingTest {
  @Test
  fun `legacy attempt ledger coercion retains truncating number behavior`() {
    assertEquals(2, 2.7.asLenientIntOrNull())
    assertEquals(3, "3".asLenientIntOrNull())
    assertNull("not-an-integer".asLenientIntOrNull())
  }
}
