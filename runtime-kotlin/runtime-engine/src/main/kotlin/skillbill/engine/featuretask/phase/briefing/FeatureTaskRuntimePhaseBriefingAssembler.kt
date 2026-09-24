package skillbill.engine.featuretask.phase.briefing

import skillbill.agentaddon.model.HydratedAgentAddonSelection
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimeBriefingProjectionInputs
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.phase.prompt.compose.FeatureTaskRuntimePhasePromptComposer
import skillbill.workflow.taskruntime.handoff.FeatureTaskRuntimeHandoffProjectionValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionShape
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeHandoffProjectionBudget
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeHandoffSourceRef
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseHandoff
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.FeatureTaskRuntimeHandoffPromptVisibility
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.FeatureTaskRuntimeRunInvariantPromptField
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeSharedReviewEvidenceReference
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

object FeatureTaskRuntimeRunInvariantPromptAllowlist {
  private val IDENTITY_CEREMONY_AND_POLICY: Set<FeatureTaskRuntimeRunInvariantPromptField> =
    setOf(
      FeatureTaskRuntimeRunInvariantPromptField.SPEC_REFERENCE,
      FeatureTaskRuntimeRunInvariantPromptField.FEATURE_SIZE,
      FeatureTaskRuntimeRunInvariantPromptField.CEREMONY_SCALING,
      FeatureTaskRuntimeRunInvariantPromptField.MANDATES_AND_OVERRIDES,
    )

  private val ACCEPTANCE_CONTRACT_PHASES: Set<FeatureTaskRuntimeRunInvariantPromptField> =
    IDENTITY_CEREMONY_AND_POLICY + FeatureTaskRuntimeRunInvariantPromptField.ACCEPTANCE_CRITERIA

  private val FINALIZATION: Set<FeatureTaskRuntimeRunInvariantPromptField> =
    IDENTITY_CEREMONY_AND_POLICY + FeatureTaskRuntimeRunInvariantPromptField.FINALIZATION_CONTEXT

  private val FINALIZATION_PHASE_IDS: Set<String> =
    setOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY,
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH,
    )

  fun forPhase(phaseId: String): Set<FeatureTaskRuntimeRunInvariantPromptField> =
    when (phaseId) {
      in FINALIZATION_PHASE_IDS -> FINALIZATION
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW -> IDENTITY_CEREMONY_AND_POLICY
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR -> ACCEPTANCE_CONTRACT_PHASES
      else -> ACCEPTANCE_CONTRACT_PHASES
    }
}

object FeatureTaskRuntimePhaseBriefingAssembler {
  fun assemble(
    handoff: FeatureTaskRuntimePhaseHandoff,
    workflowId: String? = null,
    planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
    agentAddonSelection: HydratedAgentAddonSelection = HydratedAgentAddonSelection(),
    sharedReviewEvidence: FeatureTaskRuntimeSharedReviewEvidenceReference? = null,
  ): FeatureTaskRuntimePhaseLaunchBriefing {
    val boundedAddonSelection =
      FeatureTaskRuntimePhasePromptComposer.budgetedAddonsFor(
        agentAddonSelection,
      )
    val promptDeclarations =
      handoff.projectionDeclarations +
        invariantDeclarations(handoff.phaseId) +
        boundedAddonSelection.entries.map { entry ->
          val slug = entry.persisted.slug
          PhaseHandoffProjectionDeclaration(
            consumerPhaseId = handoff.phaseId,
            sourceRef = FeatureTaskRuntimeHandoffSourceRef.AddonContentRef(slug),
            shape =
              PhaseHandoffProjectionShape(
                projectionName = "agent_addon_${slug.replace('-', '_')}",
                projectionContractId = "feature_task_runtime.agent_addon_content",
                projectionContractVersion = "0.1",
                promptVisibility = FeatureTaskRuntimeHandoffPromptVisibility.PROMPT_VISIBLE,
                budget = FeatureTaskRuntimeHandoffProjectionBudget.ADDON_CONTENT,
                declaredFieldNames = listOf(FeatureTaskRuntimeHandoffProjectionValidator.ADDON_CONTENT_FIELD),
              ),
          )
        }
    val envelope =
      FeatureTaskRuntimeHandoffProjectionValidator.validate(
        briefingProjectionInputs(
          FeatureTaskRuntimeBriefingProjectionInputs(
            handoff = handoff,
            declarations = promptDeclarations,
            workflowId = workflowId,
            planningProjectionValidator = planningProjectionValidator,
            sharedReviewEvidence = sharedReviewEvidence,
            addonContentBySlug = boundedAddonSelection.entries.associate { it.persisted.slug to it.content },
          ),
        ),
      )
    val projectedHandoff = handoff.copy(projectionDeclarations = promptDeclarations)
    val briefingText = renderFeatureTaskRuntimePhaseBriefing(projectedHandoff, envelope)
    return FeatureTaskRuntimePhaseLaunchBriefing(
      phaseId = handoff.phaseId,
      specReference = handoff.runInvariants.specReference,
      featureSize = handoff.runInvariants.featureSize.name,
      acceptanceCriteria = handoff.runInvariants.acceptanceCriteria,
      mandatesAndOverrides = handoff.runInvariants.mandatesAndOverrides,
      handoffEnvelope = envelope,
      derivedContextKeys = handoff.derivedContextKeys,
      briefingText = briefingText,
      drivingVerdict = handoff.drivingVerdict?.wireValue,
    )
  }

  private fun invariantDeclarations(phaseId: String): List<PhaseHandoffProjectionDeclaration> =
    FeatureTaskRuntimeRunInvariantPromptAllowlist.forPhase(phaseId).map { field ->
      val source =
        if (field == FeatureTaskRuntimeRunInvariantPromptField.CEREMONY_SCALING) {
          FeatureTaskRuntimeHandoffSourceRef.DerivedCeremonyScaling
        } else {
          FeatureTaskRuntimeHandoffSourceRef.RunInvariantField(field)
        }
      val projectedField =
        if (field == FeatureTaskRuntimeRunInvariantPromptField.CEREMONY_SCALING) {
          FeatureTaskRuntimeHandoffProjectionValidator.CEREMONY_SCALING_FIELD
        } else {
          field.wireValue
        }
      PhaseHandoffProjectionDeclaration(
        consumerPhaseId = phaseId,
        sourceRef = source,
        shape =
          PhaseHandoffProjectionShape(
            projectionName = "run_invariant_${field.wireValue}",
            projectionContractId = "feature_task_runtime.run_invariant",
            projectionContractVersion = "0.1",
            promptVisibility = FeatureTaskRuntimeHandoffPromptVisibility.PROMPT_VISIBLE,
            budget = FeatureTaskRuntimeHandoffProjectionBudget.PHASE_RECEIPT,
            declaredFieldNames = listOf(projectedField),
          ),
      )
    }
}
