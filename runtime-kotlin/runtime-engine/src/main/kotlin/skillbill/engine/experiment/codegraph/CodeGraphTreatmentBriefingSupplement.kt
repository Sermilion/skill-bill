package skillbill.engine.experiment.codegraph

import me.tatarka.inject.annotations.Inject
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeGoalContinuationContext
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.experiment.model.ExperimentArmId
import skillbill.ports.experiment.codegraph.CodeGraphRetrievalPort
import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
import skillbill.ports.experiment.codegraph.model.CodeGraphCandidateHit
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

private const val CODEGRAPH_CAPABILITY: String = "codegraph"
private const val MAX_CANDIDATES: Int = 16

private val GRAPH_CONSUMPTION_PHASES: Set<String> = setOf(
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN,
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN,
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT,
  FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX,
)

@Inject
class CodeGraphTreatmentBriefingSupplement(
  private val retrievalPort: CodeGraphRetrievalPort?,
  private val usageLedger: CodeGraphUsageLedgerPort,
) {
  fun phaseSupplement(request: FeatureTaskRuntimeRunRequest, phaseId: String): String {
    val continuation = request.goalContinuation ?: return ""
    if (!isEligible(continuation, phaseId)) return ""
    val pairId = continuation.experimentPairId ?: "anonymous"
    val port = retrievalPort ?: return degradedNote(pairId, "CodeGraph retrieval is unavailable.")
    val graphIndex = request.repoRoot.resolve(".skill-bill/graph-index")
    return runCatching {
      val result = port.query(
        CodeGraphQueryRequest(
          worktreeRoot = request.repoRoot,
          queryText = request.issueKey,
          graphIndexDirectory = graphIndex,
          pairId = pairId,
        ),
      )
      renderCandidates(request.issueKey, result.hits)
    }.getOrElse { error ->
      usageLedger.markDegraded(pairId, error.message ?: error.javaClass.simpleName)
      degradedNote(pairId, error.message ?: "CodeGraph query failed.")
    }
  }

  private fun isEligible(continuation: FeatureTaskRuntimeGoalContinuationContext, phaseId: String): Boolean =
    continuation.experimentArmId == ExperimentArmId.TREATMENT &&
      CODEGRAPH_CAPABILITY in continuation.experimentTreatmentCapabilities &&
      phaseId in GRAPH_CONSUMPTION_PHASES

  private fun renderCandidates(issueKey: String, hits: List<CodeGraphCandidateHit>): String = if (hits.isEmpty()) {
    "\n## CodeGraph candidates\nNo graph hits for '$issueKey'.\n"
  } else {
    buildString {
      appendLine()
      appendLine("## CodeGraph candidates (treatment arm)")
      appendLine("Authoritative source still comes from repository readers at the reviewed revision.")
      hits.take(MAX_CANDIDATES).forEach { hit ->
        appendLine("- ${hit.kind}: ${hit.name} @ ${hit.file}${hit.line?.let { ":$it" } ?: ""}")
      }
      appendLine()
    }
  }

  private fun degradedNote(pairId: String?, reason: String): String {
    pairId?.let { usageLedger.markDegraded(it, reason) }
    return "\n## CodeGraph candidates\nTreatment graph retrieval degraded: $reason\n"
  }
}
