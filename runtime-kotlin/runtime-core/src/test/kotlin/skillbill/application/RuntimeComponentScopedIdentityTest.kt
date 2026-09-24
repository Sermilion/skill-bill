package skillbill.application

import skillbill.di.core.OptionalCallbacks
import skillbill.di.core.RuntimeComponent
import skillbill.di.core.RuntimeContext
import skillbill.di.core.TransportContext
import skillbill.di.core.WorkflowOpsContext
import skillbill.di.core.create
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.model.EnvironmentContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RuntimeComponentScopedIdentityTest {
  @Test
  fun `scoped connection holder returns the same instance across accessor reads`() {
    val component = runtimeComponent(Files.createTempDirectory("skillbill-scoped-identity"))
    val first = component.featureTaskRuntimeWorkerCoordinator
    val second = component.featureTaskRuntimeWorkerCoordinator
    assertSame(first, second)
  }

  @Test
  fun `goal runner store accessors resolve over the component database`() {
    val component = runtimeComponent(Files.createTempDirectory("skillbill-goal-runner-stores"))
    component.goalRunnerManifestStore.persistControlState(
      "wftr-scoped-store",
      GoalRunnerControlState(stopAfterSubtaskId = 2),
    )

    assertEquals(2, component.goalRunnerManifestStore.controlState("wftr-scoped-store").stopAfterSubtaskId)
    assertNull(component.goalRunnerWorkflowOutcomeStore.goalSubtaskReviewState("wftr-scoped-store"))
  }

  private fun runtimeComponent(userHome: Path): RuntimeComponent =
    RuntimeComponent::class.create(
      RuntimeContext(
        environment = EnvironmentContext(environment = emptyMap(), userHome = userHome),
        transport = TransportContext(),
        workflowOps = WorkflowOpsContext(),
        callbacks = OptionalCallbacks(),
      ),
    )
}
