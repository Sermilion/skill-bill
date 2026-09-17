package skillbill.goalrunner.model

import skillbill.error.InvalidWorkflowStateSchemaError
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoalRunnerExecutionLeaseInstantTest {
  @Test
  fun `malformed execution lease timestamp fails typed at construction`() {
    assertFailsWith<InvalidWorkflowStateSchemaError> {
      GoalRunnerExecutionLease(
        generation = 1,
        ownerToken = "owner",
        hostIdentity = "host",
        bootIdentity = "boot",
        pid = 1,
        processBirthToken = "birth",
        heartbeatAt = "not-a-timestamp",
        expiresAt = "2026-01-01T00:00:01Z",
      )
    }
  }

  @Test
  fun `supported execution lease timestamps parse once for liveness comparisons`() {
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 1,
      processBirthToken = "birth",
      heartbeatAt = "2026-01-01T00:00:00Z",
      expiresAt = "2026-01-01T00:00:30Z",
    )
    assertEquals("2026-01-01T00:00:30Z", lease.expiresAtInstant.toString())
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), lease.heartbeatAtInstant)
  }
}
