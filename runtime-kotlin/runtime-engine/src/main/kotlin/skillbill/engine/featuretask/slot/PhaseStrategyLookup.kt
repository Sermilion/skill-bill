package skillbill.engine.featuretask.slot

import skillbill.workflow.taskruntime.model.core.PhaseSlot

class PhaseStrategyLookup(
  val registry: PhaseStrategyRegistry,
  private val selection: PhaseStrategySelection,
) {
  fun strategyFor(
    stepId: String,
    facts: PhaseStrategySelectionFacts,
  ): PhaseStrategy {
    val slot = PhaseSlot.slotForStep(stepId)
    return registry.strategy(slot, selection.strategyIdFor(slot, facts))
  }
}
