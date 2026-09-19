package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseHandoffSchemaError
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.review.model.ReviewFindingVerdict
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.model.audit.MAX_ACCEPTANCE_CRITERION_ORDINAL
import skillbill.workflow.taskruntime.model.audit.entries
import skillbill.workflow.taskruntime.model.audit.map
import skillbill.workflow.taskruntime.model.audit.producingPhaseId
import skillbill.workflow.taskruntime.model.audit.reason
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.core.Map
import skillbill.workflow.taskruntime.model.core.map
import skillbill.workflow.taskruntime.model.core.reason
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.feature.entries
import skillbill.workflow.taskruntime.model.feature.map
import skillbill.workflow.taskruntime.model.handoff.Map
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.handoff.consumerPhaseId
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.handoff.name
import skillbill.workflow.taskruntime.model.handoff.producingPhaseId
import skillbill.workflow.taskruntime.model.handoff.projectionName
import skillbill.workflow.taskruntime.model.handoff.sourceRef
import skillbill.workflow.taskruntime.model.persistence.artifact.map
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.reason
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.keys
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.phase.Map
import skillbill.workflow.taskruntime.model.phase.consumerPhaseId
import skillbill.workflow.taskruntime.model.phase.map
import skillbill.workflow.taskruntime.model.phase.name
import skillbill.workflow.taskruntime.model.phase.reason
import skillbill.workflow.taskruntime.model.phase.sourceLabel
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeRepairLedger
import skillbill.workflow.taskruntime.model.repair.task.entries
import skillbill.workflow.taskruntime.model.repair.task.reason
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.validation.Map
import skillbill.workflow.taskruntime.model.validation.entries
import skillbill.workflow.taskruntime.model.validation.map
import skillbill.workflow.taskruntime.model.validation.reason
import skillbill.workflow.taskruntime.model.validation.wireValue

data class FeatureTaskRuntimeRunInvariants(

  val specReference: String,

  val featureSize: FeatureTaskRuntimeFeatureSize = FeatureTaskRuntimeFeatureSize.DEFAULT,

  val acceptanceCriteria: List<String>,

  val mandatesAndOverrides: List<String>,

  val codeReviewMode: CodeReviewExecutionMode = CodeReviewExecutionMode.DEFAULT,

  val agentAddonSelection: AgentAddonSelection = AgentAddonSelection(),
) {
  init {
    require(specReference.isNotBlank()) {
      "FeatureTaskRuntimeRunInvariants.specReference must be a non-blank spec reference; " +
        "run-invariants cannot be partially specified."
    }
    require(acceptanceCriteria.isNotEmpty()) {
      "FeatureTaskRuntimeRunInvariants.acceptanceCriteria must list at least one criterion; " +
        "a run with no acceptance criteria has no contract to satisfy."
    }
    require(acceptanceCriteria.none(String::isBlank)) {
      "FeatureTaskRuntimeRunInvariants.acceptanceCriteria must not contain blank entries."
    }
    require(acceptanceCriteria.size <= MAX_ACCEPTANCE_CRITERION_ORDINAL) {
      "FeatureTaskRuntimeRunInvariants.acceptanceCriteria supports at most " +
        "$MAX_ACCEPTANCE_CRITERION_ORDINAL criteria, had ${acceptanceCriteria.size}."
    }
  }
}

enum class FeatureTaskRuntimeFeatureSize {
  SMALL,
  MEDIUM,
  LARGE,
  ;

  companion object {
    val DEFAULT: FeatureTaskRuntimeFeatureSize = MEDIUM

    fun fromWire(value: String): FeatureTaskRuntimeFeatureSize =
      entries.firstOrNull { it.name == value.trim().uppercase() }
        ?: throw InvalidFeatureTaskRuntimePhaseHandoffSchemaError(
          sourceLabel = "<wire>",
          reason = "Unknown feature-task-runtime feature size '$value'.",
        )
  }
}

enum class FeatureTaskRuntimePreplanCeremony(val wireValue: String, val promptLabel: String) {
  LIGHT("light", "lighter preplan focused on the current unit of work"),
  FULL("full", "full preplan covering boundaries, risks, rollout, and unknowns"),
}

enum class FeatureTaskRuntimeReviewScope(val wireValue: String, val promptLabel: String) {
  CURRENT_UNIT_OF_WORK("current_unit_of_work", "current-unit-of-work review scope"),
  BRANCH_DIFF("branch_diff", "branch-diff review scope"),
}

enum class FeatureTaskRuntimeAuditCeremony(val wireValue: String, val promptLabel: String) {
  LIGHT("light", "lighter audit over the current unit of work and every listed criterion"),
  FULL_PER_CRITERION("full_per_criterion", "full per-criterion completeness audit"),
}

data class FeatureTaskRuntimeCeremonyScaling(
  val preplanCeremony: FeatureTaskRuntimePreplanCeremony,
  val reviewScope: FeatureTaskRuntimeReviewScope,
  val auditCeremony: FeatureTaskRuntimeAuditCeremony,
) {
  fun toBriefingLines(): List<String> = listOf(
    "preplan_ceremony: ${preplanCeremony.wireValue} (${preplanCeremony.promptLabel})",
    "review_scope: ${reviewScope.wireValue} (${reviewScope.promptLabel})",
    "audit_ceremony: ${auditCeremony.wireValue} (${auditCeremony.promptLabel})",
  )
}

data class FeatureTaskRuntimePhaseOutput(

  val phaseId: String,

  val iteration: Int,

  val payload: String,

  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput? = null,

  val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence? = null,
) {
  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimePhaseOutput.phaseId must be non-blank." }
    require(iteration >= 1) { "FeatureTaskRuntimePhaseOutput.iteration must be >= 1, was $iteration." }
  }
}

data class FeatureTaskRuntimeResolvedUpstreamOutputs(
  val outputsByPhaseId: Map<String, FeatureTaskRuntimePhaseOutput>,
)

data class NormalizedFeatureTaskRuntimePhaseOutput(
  val canonicalJson: String,
  internal val envelope: Map<String, Any?>,
) {
  fun envelopePayload(): Any = envelope
}

data class FeatureTaskRuntimePhaseHandoff(
  val phaseId: String,
  val runInvariants: FeatureTaskRuntimeRunInvariants,
  val upstreamOutputs: FeatureTaskRuntimeResolvedUpstreamOutputs,
  val derivedContextKeys: List<String>,

  val projectionDeclarations: List<PhaseHandoffProjectionDeclaration> = emptyList(),

  val repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint? = null,

  val expectedRepositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint? = null,
  val branchIdentity: String? = null,
  val baseBranch: String = "main",

  val validationDepth: ValidationDepth = ValidationDepth.DEFAULT,
  val qualityGateSelection: FeatureTaskRuntimeQualityGateSelection = FeatureTaskRuntimeQualityGateSelection.VALIDATE,

  val drivingVerdict: FeatureTaskRuntimeVerdict? = null,
  val repairLedger: FeatureTaskRuntimeRepairLedger? = null,
  val recordedFindingVerdicts: List<ReviewFindingVerdict> = emptyList(),
)

data class FeatureTaskRuntimePhaseDeclaration(
  val phaseId: String,

  val projectionDeclarations: List<PhaseHandoffProjectionDeclaration>,

  val derivedContextKeys: List<String>,
) {
  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimePhaseDeclaration.phaseId must be non-blank." }
    require(projectionDeclarations.all { it.consumerPhaseId == phaseId }) {
      "FeatureTaskRuntimePhaseDeclaration '$phaseId' carries a projection declared for another consumer phase."
    }
    val names = projectionDeclarations.map { it.projectionName }
    require(names.distinct().size == names.size) {
      "FeatureTaskRuntimePhaseDeclaration '$phaseId' declares duplicate projection names: " +
        "${names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys}."
    }
  }

  val consumedUpstreamPhaseIds: List<String>
    get() = projectionDeclarations
      .mapNotNull { (it.sourceRef as? FeatureTaskRuntimeHandoffSourceRef.UpstreamPhaseOutput)?.producingPhaseId }
      .distinct()
}
