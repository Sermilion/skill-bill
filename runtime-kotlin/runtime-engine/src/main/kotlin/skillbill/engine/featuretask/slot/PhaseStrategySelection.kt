package skillbill.engine.featuretask.slot

import skillbill.error.featuretask.UnknownPhaseStrategyError
import skillbill.error.featuretask.UnregisteredPhaseStrategySelectionError
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.core.PhaseSlot

data class PhaseStrategySelectionFacts(
  val codeReviewMode: CodeReviewExecutionMode,
  val qualityGate: FeatureTaskRuntimeQualityGateSelection,
)

sealed interface PhaseStrategyBinding {
  val strategyIds: Set<String>

  fun resolve(facts: PhaseStrategySelectionFacts): String?

  data class Fixed(val strategyId: String) : PhaseStrategyBinding {
    override val strategyIds: Set<String> get() = setOf(strategyId)

    override fun resolve(facts: PhaseStrategySelectionFacts): String = strategyId
  }

  data class ByCodeReviewMode(val ids: Map<CodeReviewExecutionMode, String>) : PhaseStrategyBinding {
    override val strategyIds: Set<String> get() = ids.values.toSet()

    override fun resolve(facts: PhaseStrategySelectionFacts): String? = ids[facts.codeReviewMode]
  }

  data class ByQualityGate(val ids: Map<FeatureTaskRuntimeQualityGateSelection, String>) : PhaseStrategyBinding {
    override val strategyIds: Set<String> get() = ids.values.toSet()

    override fun resolve(facts: PhaseStrategySelectionFacts): String? = ids[facts.qualityGate]
  }
}

class PhaseStrategySelection(
  registry: PhaseStrategyRegistry,
  private val bindings: Map<PhaseSlot, PhaseStrategyBinding>,
) {
  init {
    bindings.forEach { (slot, binding) ->
      binding.strategyIds.firstOrNull { !registry.contains(slot, it) }?.let { strategyId ->
        throw UnregisteredPhaseStrategySelectionError(slot.wireValue, strategyId)
      }
    }
  }

  fun strategyIdFor(
    slot: PhaseSlot,
    facts: PhaseStrategySelectionFacts,
  ): String =
    bindings[slot]?.resolve(facts)
      ?: throw UnknownPhaseStrategyError(
        slot.wireValue,
        "code_review_mode=${facts.codeReviewMode.wireValue}, quality_gate=${facts.qualityGate.wireValue}",
      )
}
