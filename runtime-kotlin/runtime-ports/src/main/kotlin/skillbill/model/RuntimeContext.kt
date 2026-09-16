package skillbill.model

import skillbill.ports.agentrun.AgentRunLauncher
import skillbill.ports.agentrun.ExecutableLookup
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.review.ReviewNativeAgentPreflightPort
import skillbill.ports.system.HostPlatformPort
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.time.RuntimeTimingPort
import skillbill.ports.process.InstallerProcessPort
import skillbill.ports.process.InstallerScriptFetchPort
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import java.nio.file.Path
import java.time.Duration

data class EnvironmentContext(
  val dbPathOverride: String? = null,
  val stdinText: String? = null,
  val environment: Map<String, String> = UnspecifiedEnvironment,
  val userHome: Path = UnspecifiedUserHome,
  val repositoryRoot: Path = UnspecifiedRepositoryRoot,
) {
  companion object {
    val UnspecifiedEnvironment: Map<String, String> = object : AbstractMap<String, String>() {
      override val entries: Set<Map.Entry<String, String>> = emptySet()
    }
    val UnspecifiedUserHome: Path = Path.of(".skillbill-unspecified-user-home")
    val UnspecifiedRepositoryRoot: Path = Path.of(".skillbill-unspecified-repository-root")
  }
}

data class TransportContext(
  val requester: RemoteTransportPort? = null,
  val connectTimeout: Duration? = null,
  val requestTimeout: Duration? = null,
)

data class WorkflowOpsContext(val workflowGitOperations: WorkflowGitOperations? = null)

data class OptionalCallbacks(
  val agentRunLauncher: AgentRunLauncher? = null,
  val goalPullRequestPort: GoalPullRequestPort? = null,
  val executableLookup: ExecutableLookup? = null,
  val reviewNativeAgentPreflight: ReviewNativeAgentPreflightPort? = null,
  val runtimeTimingPort: RuntimeTimingPort? = null,
  val hostPlatformPort: HostPlatformPort? = null,
  val installerProcessPort: InstallerProcessPort? = null,
  val installerScriptFetchPort: InstallerScriptFetchPort? = null,
)

data class RuntimeContext(
  val environment: EnvironmentContext,
  val transport: TransportContext,
  val workflowOps: WorkflowOpsContext,
  val callbacks: OptionalCallbacks,
) {
  constructor(
    stdinText: String? = null,
    environment: Map<String, String> = EnvironmentContext.UnspecifiedEnvironment,
    userHome: Path = EnvironmentContext.UnspecifiedUserHome,
    repositoryRoot: Path = EnvironmentContext.UnspecifiedRepositoryRoot,
    requester: RemoteTransportPort? = null,
    workflowGitOperations: WorkflowGitOperations? = null,
    agentRunLauncher: AgentRunLauncher? = null,
    goalPullRequestPort: GoalPullRequestPort? = null,
    executableLookup: ExecutableLookup? = null,
    reviewNativeAgentPreflight: ReviewNativeAgentPreflightPort? = null,
    runtimeTimingPort: RuntimeTimingPort? = null,
    hostPlatformPort: HostPlatformPort? = null,
    installerProcessPort: InstallerProcessPort? = null,
    installerScriptFetchPort: InstallerScriptFetchPort? = null,
  ) : this(
    EnvironmentContext(
      stdinText = stdinText,
      environment = environment,
      userHome = userHome,
      repositoryRoot = repositoryRoot,
    ),
    TransportContext(requester),
    WorkflowOpsContext(workflowGitOperations),
    OptionalCallbacks(
      agentRunLauncher,
      goalPullRequestPort,
      executableLookup,
      reviewNativeAgentPreflight,
      runtimeTimingPort,
      hostPlatformPort,
      installerProcessPort,
      installerScriptFetchPort,
    ),
  )

  companion object {
    val UnspecifiedEnvironment = EnvironmentContext.UnspecifiedEnvironment
    val UnspecifiedUserHome = EnvironmentContext.UnspecifiedUserHome
    val UnspecifiedRepositoryRoot = EnvironmentContext.UnspecifiedRepositoryRoot
  }
}
