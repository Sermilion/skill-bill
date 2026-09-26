package skillbill.engine.featuretask.runloop.core

import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseRunner
import skillbill.error.core.DatabaseBusyError
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val BUSY_MESSAGE = "[SQLITE_BUSY] The database file is locked (database is locked)"

class FeatureTaskRuntimeGoalReviewPreparationDispositionTest {
  @Test
  fun `a typed busy failure is retryable and keeps its persisted block reason text`() {
    val error = DatabaseBusyError(IllegalStateException(BUSY_MESSAGE))

    assertEquals(
      FeatureTaskRuntimeFailureDisposition.RETRYABLE,
      FeatureTaskRuntimeRunLoopPhaseRunner.goalReviewPreparationDisposition(error),
    )
    val reason = FeatureTaskRuntimeRunLoopPhaseRunner.goalReviewPreparationFailure("reservation", error)
    assertTrue(reason.startsWith("Goal-subtask review reservation failed"), reason)
    assertTrue(reason.endsWith(": $BUSY_MESSAGE"), reason)
  }

  @Test
  fun `an untyped failure carrying the same message needs user action`() {
    val error = RuntimeException(BUSY_MESSAGE)

    assertEquals(
      FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
      FeatureTaskRuntimeRunLoopPhaseRunner.goalReviewPreparationDisposition(error),
    )
  }

  @Test
  fun `a busy failure wrapped deeper in the cause chain stays retryable`() {
    val error = RuntimeException("review reservation failed", DatabaseBusyError(IllegalStateException(BUSY_MESSAGE)))

    assertEquals(
      FeatureTaskRuntimeFailureDisposition.RETRYABLE,
      FeatureTaskRuntimeRunLoopPhaseRunner.goalReviewPreparationDisposition(error),
    )
  }
}
