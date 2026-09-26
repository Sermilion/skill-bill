package skillbill.engine.featuretask.slot

import skillbill.application.review.service.RuntimeOwnedReviewMode
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.review.model.ReviewFindingVerdict
import skillbill.workflow.model.goalreview.ReviewPassResolution
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition

/**
 * The launch and output behaviour one strategy step adds to the shared attempt path. The shared launch
 * preparation and output gate ask the strategy of the running step for its hooks, so step-specific evidence,
 * prompt sections, and output checks live with the step instead of in the shared code.
 */
internal interface PhaseStepHooks {
  /** Whether the output gate fingerprints the repository when this step completes. */
  val fingerprintsCompletedRepository: Boolean
    get() = false

  /** The prompt sections appended after the composed launch prompt of [run]. */
  fun launchPromptSupplement(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): String = ""

  /** The repository checkpoint the launch handoff of [run] expects, given the [current] checkpoint fingerprint. */
  fun expectedLaunchCheckpoint(
    run: PhaseRun,
    current: String?,
  ): String? = run.reentry?.expectedRepositoryCheckpoint ?: current

  /** The review pass and review tier the launch prompt of [run] carries. */
  fun launchReviewTier(
    run: PhaseRun,
    state: PhaseRunState,
  ): PhaseLaunchReviewTier =
    PhaseLaunchReviewTier(
      passNumber = null,
      resolution = null,
      executedTier = RuntimeOwnedReviewMode.execute(run.request.runInvariants.codeReviewMode),
    )

  /** The recorded finding verdicts the launch handoff of this step carries. */
  fun handoffFindingVerdicts(state: PhaseRunState): List<ReviewFindingVerdict> = emptyList()

  /** Retains step evidence from [outputText] after the output gate rejected its schema. */
  fun retainSchemaRejectedOutput(
    state: PhaseRunState,
    outputText: String,
  ) = Unit

  /**
   * The outcome [outputText] of [run] settles to before the shared output gate decodes it, or null when the shared
   * gate decodes it. A runtime-owned step settles its triage and repair sessions here.
   */
  fun earlyOutput(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
  ): PhaseOutcome? = null

  /** Checks validated [outputMap] of [run] before the shared output checks run. */
  fun checkValidatedOutput(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): PhaseStepOutputCheck = PhaseStepOutputCheck.Accept

  /** The reason completed [outputMap] of [run] cannot settle, or null when it can. */
  fun completionRejection(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): String? = null

  /** Settles the step records that completed [outputMap] of [run] carries, once every completion check passed. */
  fun settleCompletedOutput(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): PhaseStepOutputCheck = PhaseStepOutputCheck.Accept

  /** Records step evidence from accepted [outputMap] of [run] before the completed step is persisted. */
  fun recordAcceptedOutput(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ) = Unit

  companion object {
    val None: PhaseStepHooks = object : PhaseStepHooks {}
  }
}

internal data class PhaseLaunchReviewTier(
  val passNumber: Int?,
  val resolution: ReviewPassResolution?,
  val executedTier: CodeReviewExecutionMode,
)

/** The verdict of a step's check over its validated output. */
internal sealed interface PhaseStepOutputCheck {
  data object Accept : PhaseStepOutputCheck

  data class Reject(
    val reason: String,
    val rule: String = OUTPUT_VERIFICATION_RULE,
  ) : PhaseStepOutputCheck

  data class Redeliver(val reason: String) : PhaseStepOutputCheck

  data class Block(
    val reason: String,
    val disposition: FeatureTaskRuntimeFailureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
  ) : PhaseStepOutputCheck

  /** The step keeps repairing in its session; [previousValue] is handed to the next attempt. */
  data class ContinueRepair(val previousValue: String) : PhaseStepOutputCheck

  companion object {
    const val OUTPUT_VERIFICATION_RULE = "output-verification"
  }
}
