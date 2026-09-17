package skillbill.architecture

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureTaskRuntimeParameterBagArchitectureTest {
  @Test
  fun `single-use facts and port bags stay dissolved`() {
    val runtimeEngine = ArchitectureScanSupport.runtimeRoot.resolve(
      "runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine",
    )
    val production = ArchitectureScanSupport.kotlinFilesUnder(runtimeEngine)
      .joinToString("\n") { path -> path.readText() }

    assertFalse(production.contains("SettleValidationGateCycleArgs"))
    assertFalse(production.contains("RuntimeOwnedValidationSettlementArgs"))
    assertFalse(production.contains("ProducePlanArgs"))
    assertFalse(production.contains("RuntimeOwnedBuildSettlementArgs"))
    assertFalse(production.contains("BuildGateRunningPhaseArgs"))
    assertFalse(production.contains("SettleBuildGateCycleResultArgs"))
    assertFalse(production.contains("PersistRuntimeOwnedBuildCompletionArgs"))
    assertFalse(production.contains("RequiredValidationCommandArgs"))
    assertTrue(production.contains("internal class ValidationGateCycleSettlement("))
    assertTrue(production.contains("internal class RuntimeOwnedValidationSettlement("))
    assertTrue(
      Regex(
        """internal fun DefaultGoalPlanningSweep\.producePlan\(\s*shared:\s*GoalPlanningSharedContext""",
      ).containsMatchIn(production),
    )
    assertTrue(
      Regex(
        """class RuntimeOwnedValidationSettlement\([\s\S]*?private val recorder:\s*FeatureTaskRuntimePhaseRecorder[\s\S]*?private val outputValidator:\s*FeatureTaskRuntimePhaseOutputValidator[\s\S]*?private val phaseGates:\s*FeatureTaskRuntimePhaseGates""",
      ).containsMatchIn(production),
    )
  }
}
