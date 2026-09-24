package skillbill.di.core
import skillbill.error.core.UnresolvedRemoteTransportPortError
import skillbill.infrastructure.host.CanonicalRepositoryRoot
import skillbill.infrastructure.http.JdkHttpRemoteTransport
import skillbill.infrastructure.sqlite.SQLiteDatabaseSessionFactory
import skillbill.model.EnvironmentContext
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.repository.RepositoryEnclosingRootPort
import skillbill.ports.telemetry.transport.RemoteTransportPort
import skillbill.ports.workflow.WorkflowSnapshotValidator
import java.nio.file.Path
import java.time.Clock

internal object RuntimeBootstrapBindings {
  fun repositoryEnclosingRootPort(): RepositoryEnclosingRootPort = CanonicalRepositoryRoot

  fun runtimeContext(inputRuntimeContext: RuntimeContext): RuntimeContext {
    val repositoryEnclosingRootPort = repositoryEnclosingRootPort()
    val inputEnvironment = inputRuntimeContext.environment
    val resolvedEnvironment =
      if (inputEnvironment.userHome == EnvironmentContext.UnspecifiedUserHome) {
        inputEnvironment.copy(userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize())
      } else {
        inputEnvironment
      }
    val environmentWithEnv =
      if (resolvedEnvironment.environment === EnvironmentContext.UnspecifiedEnvironment) {
        resolvedEnvironment.copy(environment = System.getenv())
      } else {
        resolvedEnvironment
      }
    val inputTransport = inputRuntimeContext.transport
    val resolvedTransport =
      inputTransport.copy(
        requester =
          inputTransport.requester
            ?: JdkHttpRemoteTransport.create(inputTransport.connectTimeout, inputTransport.requestTimeout),
      )
    val resolvedRepositoryRoot =
      if (environmentWithEnv.repositoryRoot == EnvironmentContext.UnspecifiedRepositoryRoot) {
        environmentWithEnv.copy(repositoryRoot = repositoryEnclosingRootPort.enclosingRepositoryRoot(Path.of("")))
      } else {
        environmentWithEnv.copy(
          repositoryRoot = repositoryEnclosingRootPort.enclosingRepositoryRoot(environmentWithEnv.repositoryRoot),
        )
      }
    return inputRuntimeContext.copy(
      environment = resolvedRepositoryRoot,
      transport = resolvedTransport,
    )
  }

  fun remoteTransportPort(context: TransportContext): RemoteTransportPort =
    context.requester ?: throw UnresolvedRemoteTransportPortError()

  fun databaseSessionFactory(
    context: EnvironmentContext,
    clock: Clock,
    diagnostics: RuntimeDiagnostics,
    workflowSnapshotValidator: WorkflowSnapshotValidator,
  ): DatabaseSessionFactory =
    SQLiteDatabaseSessionFactory(context, clock, diagnostics, workflowSnapshotValidator)
}
