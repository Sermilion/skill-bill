package skillbill.cli.featuretask

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import me.tatarka.inject.annotations.Inject
import skillbill.application.workflow.WorkflowService
import skillbill.cli.kernel.CliRunState
import skillbill.cli.kernel.DocumentedCliCommand
import skillbill.cli.kernel.resolveCliRepositoryRoot
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskContinuationLookupService
import skillbill.engine.featuretask.runner.FeatureTaskRuntimeStatusService
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeStatusRequest

private const val FEATURE_TASK_RUNTIME_DEPRECATION_NOTE: String =
  "feature-task-runtime is a deprecated alias for feature-task. Use feature-task; behavior is unchanged.\n"

@Inject
class FeatureTaskRuntimeDeprecatedRunCommand(
  private val deps: FeatureTaskRuntimeRunDependencies,
  private val workflowService: WorkflowService,
  featureTaskRuntimeDeprecatedExplicitRunCommand: FeatureTaskRuntimeDeprecatedExplicitRunCommand,
  featureTaskRuntimeDeprecatedStatusCommand: FeatureTaskRuntimeDeprecatedStatusCommand,
  featureTaskRuntimeDeprecatedResumeCommand: FeatureTaskRuntimeDeprecatedResumeCommand,
) : FeatureTaskRuntimePhaseAgentCommand(
  "feature-task-runtime",
  "Deprecated alias for feature-task. Use feature-task; behavior is unchanged.",
) {
  override val hiddenFromHelp: Boolean = true

  private val issueKey by argument(help = "Issue key the run implements.").optional()
  private val specPath by argument(help = "Path to the governed spec the run implements.").optional()

  override val invokeWithoutSubcommand: Boolean = true

  init {
    subcommands(
      featureTaskRuntimeDeprecatedExplicitRunCommand,
      featureTaskRuntimeDeprecatedStatusCommand,
      featureTaskRuntimeDeprecatedResumeCommand,
    )
  }

  override fun run() {
    deps.inputs.liveStderr(FEATURE_TASK_RUNTIME_DEPRECATION_NOTE)
    if (currentContext.invokedSubcommand != null) {
      return
    }
    val runIssueKey = issueKey ?: throw UsageError("issue_key is required for feature-task run.")
    val resolvedRepoRoot = resolveCliRepositoryRoot(repoRoot, deps.inputs)
    val runSpecPath = resolveSpecPath(deps, runIssueKey, specPath, resolvedRepoRoot)
    val prepared = prepareRuntimeRun(deps, resolvedRepoRoot)
    executeRuntimeRun(
      deps = deps,
      issueKey = runIssueKey,
      specPath = runSpecPath,
      prepared = prepared,
      workflowId = {
        resolveRunWorkflowId(
          workflowService,
          runIssueKey,
          runSpecPath,
          prepared.repoRoot,
        )
      },
    )
  }
}

@Inject
class FeatureTaskRuntimeDeprecatedExplicitRunCommand(
  private val deps: FeatureTaskRuntimeRunDependencies,
  private val workflowService: WorkflowService,
) : FeatureTaskRuntimePhaseAgentCommand(
  "run",
  "Run the feature-task phase loop (explicit form of the parent command's default run).",
) {
  private val issueKey by argument(help = "Issue key the run implements.")
  private val specPath by argument(help = "Path to the governed spec the run implements.").optional()

  override fun run() {
    val resolvedRepoRoot = resolveCliRepositoryRoot(repoRoot, deps.inputs)
    val runSpecPath = resolveSpecPath(deps, issueKey, specPath, resolvedRepoRoot)
    val prepared = prepareRuntimeRun(deps, resolvedRepoRoot)
    executeRuntimeRun(
      deps = deps,
      issueKey = issueKey,
      specPath = runSpecPath,
      prepared = prepared,
      workflowId = {
        resolveRunWorkflowId(
          workflowService,
          issueKey,
          runSpecPath,
          prepared.repoRoot,
        )
      },
    )
  }
}

@Inject
class FeatureTaskRuntimeDeprecatedStatusCommand(
  private val statusService: FeatureTaskRuntimeStatusService,
  private val state: CliRunState,
) : DocumentedCliCommand("status", "Show read-only feature-task phase status.") {
  private val workflowId by argument(help = "Runtime workflow id whose phase status to show.")

  override fun run() {
    val projection = statusService.status(
      FeatureTaskRuntimeStatusRequest(workflowId = workflowId),
    )
    val payload = projection.toRuntimeStatusCliMap(workflowId)
    state.completeText(runtimeStatusText(payload), payload, exitCode = payload.runtimeStatusExitCode())
  }
}

@Inject
class FeatureTaskRuntimeDeprecatedResumeCommand(
  private val deps: FeatureTaskRuntimeRunDependencies,
  private val lookupService: FeatureTaskContinuationLookupService,
) : FeatureTaskRuntimePhaseAgentCommand(
  "resume",
  "Resume a feature-task run against an existing workflow id.",
) {
  private val workflowId by argument(help = "Existing runtime workflow id to resume.")
  private val issueKey by argument(help = "Issue key the resumed run implements.")
  private val specPath by argument(help = "Path to the governed spec the run implements.")

  override fun run() {
    val resolvedRepoRoot = resolveCliRepositoryRoot(repoRoot, deps.inputs)
    val prepared = prepareRuntimeRun(deps, resolvedRepoRoot)
    verifyRuntimeResume(
      VerifyRuntimeResumeArgs(
        lookupService = lookupService,
        workflowId = workflowId,
        issueKey = issueKey,
        specPath = specPath,
        repoRoot = prepared.repoRoot,
        goalChild = goalParentIssueKey != null,
      ),
    )
    executeRuntimeRun(
      deps = deps,
      issueKey = issueKey,
      specPath = specPath,
      prepared = prepared,
      workflowId = { workflowId },
    )
  }
}
