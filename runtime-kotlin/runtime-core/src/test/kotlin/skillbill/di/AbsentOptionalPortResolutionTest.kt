package skillbill.di

import skillbill.infrastructure.http.JdkHttpRequester
import skillbill.infrastructure.workflow.GitWorkflowGitOperations
import skillbill.model.EnvironmentContext
import skillbill.model.OptionalCallbacks
import skillbill.model.RuntimeContext
import skillbill.model.TransportContext
import skillbill.model.WorkflowOpsContext
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.telemetry.model.RemoteTransportResponse
import kotlin.test.Test
import kotlin.test.assertSame

class AbsentOptionalPortResolutionTest {
  private val provides = object : RuntimeWorkflowProvides {}

  @Test
  fun `an absent requester resolves to the JDK transport`() {
    val resolved = RuntimeBootstrapBindings.runtimeContext(
      RuntimeContext(
        environment = EnvironmentContext(),
        transport = TransportContext(),
        workflowOps = WorkflowOpsContext(),
        callbacks = OptionalCallbacks(),
      ),
    )

    assertSame(JdkHttpRequester, resolved.transport.requester)
  }

  @Test
  fun `a caller-supplied requester survives bootstrap`() {
    val supplied = RemoteTransportPort { _, _, _, _ -> RemoteTransportResponse(200, "") }

    val resolved = RuntimeBootstrapBindings.runtimeContext(
      RuntimeContext(
        environment = EnvironmentContext(),
        transport = TransportContext(requester = supplied),
        workflowOps = WorkflowOpsContext(),
        callbacks = OptionalCallbacks(),
      ),
    )

    assertSame(supplied, resolved.transport.requester)
  }

  @Test
  fun `absent workflow git operations resolve to the git adapter`() {
    val git = GitWorkflowGitOperations()

    assertSame(git, provides.workflowGitOperations(WorkflowOpsContext(), git))
  }
}
