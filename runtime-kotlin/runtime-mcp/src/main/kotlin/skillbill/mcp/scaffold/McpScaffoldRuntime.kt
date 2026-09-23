package skillbill.mcp.scaffold

import skillbill.application.scaffold.model.ScaffoldInvocationArgs
import skillbill.application.scaffold.runScaffoldInvocation
import skillbill.contracts.JsonCodec
import skillbill.mcp.shared.McpComponent
import kotlin.coroutines.cancellation.CancellationException

internal object McpScaffoldRuntime {
  fun newSkillScaffold(
    payload: Map<String, Any?>,
    dryRun: Boolean = false,
    orchestrated: Boolean = false,
    component: McpComponent,
  ): Map<String, Any?> {
    val runtimeComponent = component.runtimeComponent
    val resolvedRoot = runtimeComponent.resolvedEnvironmentContext.repositoryRoot
    val outcome =
      runCatching {
        val payloadText = JsonCodec.mapToJsonString(payload)
        val invocation =
          runScaffoldInvocation(
            runtimeComponent.scaffoldGateway,
            ScaffoldInvocationArgs(
              payloadText = payloadText,
              invocationRepositoryRoot = resolvedRoot,
              dryRun = dryRun,
              registerExternalSources = false,
              userHome = runtimeComponent.resolvedEnvironmentContext.userHome,
              environment = runtimeComponent.resolvedEnvironmentContext.environment,
              clock = component.clock,
            ),
          )
        scaffoldSuccessMap(
          sessionId = invocation.sessionId,
          payload = payload,
          result = invocation.scaffoldResult,
          dryRun = dryRun,
          orchestrated = orchestrated,
        )
      }
    val error = outcome.exceptionOrNull()
    return if (error == null) {
      outcome.getOrThrow()
    } else {
      when (error) {
        is CancellationException -> throw error
        is Exception ->
          scaffoldFailureMap(
            sessionId = "nss-unknown",
            payload = payload,
            orchestrated = orchestrated,
            error = error,
          )
        else -> throw error
      }
    }
  }
}
