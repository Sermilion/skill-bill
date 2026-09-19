package skillbill.engine.goalrunner.planning.model

import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.SpecSource
import java.nio.file.Path

internal data class GoalPlanningSharedContext(
  val issueKey: String,
  val normalizedIssueKey: String,
  val parentWorkflowId: String,
  val manifest: DecompositionManifest,
  val controlState: GoalRunnerControlState,
  val repositoryIdentity: String,
  val parentSpec: String,
  val parentSpecHash: String,
  val decompositionManifestHash: String,
  val repoRoot: Path,
  val invokedAgentId: String,
  val configuredAgentOverrideId: String?,
  val specSource: SpecSource,
  val parentSpecPath: Path,
  val planningPacket: Map<String, Any?>,
)
