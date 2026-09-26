package skillbill.di.featuretask

import skillbill.di.core.OptionalCallbacks
import skillbill.di.core.RuntimeComponent
import skillbill.di.core.RuntimeContext
import skillbill.di.core.TransportContext
import skillbill.di.core.WorkflowOpsContext
import skillbill.di.core.create
import skillbill.engine.featuretask.slot.PhaseStrategySelectionFacts
import skillbill.model.EnvironmentContext
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeFeatureTaskSlotProvidesTest {
  private val strategies =
    RuntimeComponent::class.create(
      RuntimeContext(
        environment =
          EnvironmentContext(
            environment = emptyMap(),
            userHome = Files.createTempDirectory("skillbill-slot-provides"),
          ),
        transport = TransportContext(),
        workflowOps = WorkflowOpsContext(),
        callbacks = OptionalCallbacks(),
      ),
    ).featureTaskRuntimeRunner.strategies

  @Test
  fun `production registry registers one strategy per slot with a distinct runner each`() {
    val registered = strategies.registry.strategies

    assertEquals(PhaseSlot.entries.toList(), registered.map { it.slot })
    assertEquals(registered.size, registered.map { it.runner }.toSet().size)
  }

  @Test
  fun `production selection resolves every slot for every selection fact`() {
    CodeReviewExecutionMode.entries.forEach { mode ->
      FeatureTaskRuntimeQualityGateSelection.entries.forEach { gate ->
        PhaseSlot.entries.flatMap { it.steps }.forEach { step ->
          val strategy = strategies.strategyFor(step, PhaseStrategySelectionFacts(mode, gate))
          assertEquals(PhaseSlot.slotForStep(step), strategy.slot)
        }
      }
    }
  }
}
