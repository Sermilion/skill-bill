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
        """internal fun DefaultGoalPlanningSweep\.producePlan\(\s*args:\s*ProduceMissingPlansArgs""",
      ).containsMatchIn(production),
    )
    assertTrue(production.contains("private val recorder: FeatureTaskRuntimePhaseRecorder"))
    assertTrue(production.contains("private val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder"))
    assertTrue(production.contains("private val outputValidator: FeatureTaskRuntimePhaseOutputValidator"))
    assertTrue(production.contains("private val phaseGates: FeatureTaskRuntimePhaseGates"))
  }
}
