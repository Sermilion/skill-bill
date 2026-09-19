package skillbill.engine.featuretask.lifecycle.core


import skillbill.engine.featuretask.phase.prompt.directives.phaseDirectives
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class FeatureTaskRuntimeAuditTestExclusionTest {
  @Test
  fun `repair and follow-up audit directives carry no build or test execution instruction`() {
    val repairAndAudit = listOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT,
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
    ).map { phaseId -> phaseId to requireNotNull(phaseDirectives[phaseId]) }

    repairAndAudit.forEach { (phaseId, directive) ->
      BUILD_AND_TEST_COMMANDS.forEach { command ->
        assertTrue(
          !directive.contains(command),
          "the $phaseId directive must not instruct a $command invocation as repair or audit evidence",
        )
      }
    }

    assertContains(
      requireNotNull(phaseDirectives[FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT]),
      "do not run builds or tests here",
    )
    val auditDirective = requireNotNull(phaseDirectives[FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT])
    assertContains(auditDirective, "read-only repository facts")
    assertContains(auditDirective, "validation owns test execution")
    assertContains(auditDirective, "gaps")
  }

  private companion object {
    val BUILD_AND_TEST_COMMANDS = listOf("./gradlew", "gradlew check", "npm ", "npx ", "pytest", "cargo test")
  }
}
