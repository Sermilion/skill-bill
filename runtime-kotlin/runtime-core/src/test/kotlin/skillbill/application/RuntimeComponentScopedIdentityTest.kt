package skillbill.application

import skillbill.di.core.RuntimeComponent
import skillbill.di.core.create
import skillbill.model.EnvironmentContext
import skillbill.model.OptionalCallbacks
import skillbill.model.RuntimeContext
import skillbill.model.TransportContext
import skillbill.model.WorkflowOpsContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertSame

class RuntimeComponentScopedIdentityTest {
  @Test
  fun `scoped connection holder returns the same instance across accessor reads`() {
    val tempDir = Files.createTempDirectory("skillbill-scoped-identity")
    val component =
      RuntimeComponent::class.create(
        RuntimeContext(
          environment = EnvironmentContext(environment = emptyMap(), userHome = tempDir),
          transport = TransportContext(),
          workflowOps = WorkflowOpsContext(),
          callbacks = OptionalCallbacks(),
        ),
      )
    val first = component.featureTaskRuntimeWorkerCoordinator
    val second = component.featureTaskRuntimeWorkerCoordinator
    assertSame(first, second)
  }
}
