package skillbill.workflow.taskruntime.model.core
import skillbill.workflow.taskruntime.model.handoff.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.Map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.Map
import skillbill.workflow.taskruntime.model.phase.Map
import skillbill.workflow.taskruntime.model.validation.Map

object FeatureTaskRuntimeProjectionCanonicalizationFixtures {
  private val NESTED_CLOSED_OBJECTS: Map<String, Any?> = mapOf(
    "completed_task_ids" to listOf("Task-01"),
    "changed_paths" to listOf("src/Foo.kt"),
    "tests_executed" to listOf(mapOf("name" to "FooTest.kt", "outcome" to "passed")),
    "reconciliation_evidence" to mapOf("reconciled" to true, "evidence" to "  ok  "),
  )

  val ALL: List<Map<String, Any?>> = listOf(
    NESTED_CLOSED_OBJECTS,
  )
}
