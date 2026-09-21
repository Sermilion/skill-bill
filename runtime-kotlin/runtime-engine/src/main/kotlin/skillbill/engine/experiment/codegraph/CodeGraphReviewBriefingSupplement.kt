package skillbill.engine.experiment.codegraph

import me.tatarka.inject.annotations.Inject
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeGoalContinuationContext
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.experiment.model.ExperimentArmId
import skillbill.ports.experiment.codegraph.CodeGraphRetrievalPort
import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
import skillbill.ports.experiment.codegraph.model.CodeGraphCandidateHit
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.ports.review.evidence.ReviewEvidenceBrokerFactory
import skillbill.ports.review.model.ReviewEvidenceResult
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewInput
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

private const val CODEGRAPH_CAPABILITY: String = "codegraph"
private const val MAX_REVIEW_CANDIDATES: Int = 8
private const val MAX_EVIDENCE_LINES: Int = 12

@Inject
class CodeGraphReviewBriefingSupplement(
  private val retrievalPort: CodeGraphRetrievalPort?,
  private val usageLedger: CodeGraphUsageLedgerPort,
  private val reviewEvidenceBrokerFactory: ReviewEvidenceBrokerFactory,
) {
  fun phaseSupplement(
    request: FeatureTaskRuntimeRunRequest,
    phaseId: String,
    reviewInput: GoalSubtaskReviewInput?,
  ): String {
    val continuation = eligibleContinuation(request, phaseId) ?: return ""
    val pairId = continuation.experimentPairId ?: "anonymous"
    val port = retrievalPort ?: return degradedNote(pairId, "CodeGraph retrieval is unavailable.")
    val binding = reviewBinding(request, reviewInput) ?: return ""
    val graphIndex = request.repoRoot.resolve(".skill-bill/graph-index")
    val broker = reviewEvidenceBrokerFactory.brokerFor(binding)
    return runCatching {
      val result = port.query(
        CodeGraphQueryRequest(
          worktreeRoot = request.repoRoot,
          queryText = request.issueKey,
          graphIndexDirectory = graphIndex,
          pairId = pairId,
        ),
      )
      val excerpts = CodeGraphReviewEvidenceCandidates.authorizedExcerpts(
        broker = broker,
        lane = binding.assignment.lane,
        hits = result.hits,
        assignedPaths = binding.assignment.assignedPaths.toSet(),
      )
      renderEvidence(excerpts)
    }.getOrElse { error ->
      usageLedger.markDegraded(pairId, error.message ?: error.javaClass.simpleName)
      degradedNote(pairId, error.message ?: "CodeGraph review retrieval failed.")
    }
  }

  private fun reviewBinding(request: FeatureTaskRuntimeRunRequest, reviewInput: GoalSubtaskReviewInput?) =
    reviewInput?.let { input ->
      CodeGraphReviewEvidenceBrokerBinding.fromReviewInput(request.repoRoot, input)
    }

  private fun eligibleContinuation(
    request: FeatureTaskRuntimeRunRequest,
    phaseId: String,
  ): FeatureTaskRuntimeGoalContinuationContext? {
    val continuation = request.goalContinuation ?: return null
    return continuation.takeIf {
      phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
        it.experimentArmId == ExperimentArmId.TREATMENT &&
        CODEGRAPH_CAPABILITY in it.experimentTreatmentCapabilities
    }
  }

  private fun renderEvidence(excerpts: List<Pair<CodeGraphCandidateHit, ReviewEvidenceResult>>): String =
    if (excerpts.isEmpty()) {
      "\n## CodeGraph review evidence\nNo broker-authorized graph excerpts for this review revision.\n"
    } else {
      buildString {
        appendLine()
        appendLine("## CodeGraph review evidence (broker-authorized excerpts)")
        appendLine("Revision-bound reads only; graph hits outside review assignment were dropped.")
        excerpts.take(MAX_REVIEW_CANDIDATES).forEach { (hit, evidence) ->
          appendLine("- ${hit.kind}: ${hit.name} @ ${hit.file}${hit.line?.let { ":$it" } ?: ""}")
          evidence.content?.lineSequence()?.take(MAX_EVIDENCE_LINES)?.forEach { line ->
            appendLine("  $line")
          }
        }
        appendLine()
      }
    }

  private fun degradedNote(pairId: String?, reason: String): String {
    pairId?.let { usageLedger.markDegraded(it, reason) }
    return "\n## CodeGraph review evidence\nTreatment graph retrieval degraded: $reason\n"
  }
}
