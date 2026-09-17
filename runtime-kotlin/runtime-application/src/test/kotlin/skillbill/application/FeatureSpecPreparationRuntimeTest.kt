package skillbill.application

import skillbill.engine.featuretask.FeatureSpecPreparationRuntime
import skillbill.featurespec.model.FeatureSpecPreparationDecision
import skillbill.featurespec.model.FeatureSpecPreparationIntake
import skillbill.featurespec.model.FeatureSpecPreparationMode
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureSpecPreparationRuntimeTest {
  @Test
  fun `feature-spec preparation uses the injected core`() {
    var seenIntake: FeatureSpecPreparationIntake? = null
    val runtime = FeatureSpecPreparationRuntime { intake ->
      seenIntake = intake
      FeatureSpecPreparationDecision(
        issueKey = intake.issueKey,
        intendedOutcome = intake.intendedOutcome,
        acceptanceCriteria = intake.acceptanceCriteria,
        constraints = intake.constraints,
        nonGoals = intake.nonGoals,
        mode = FeatureSpecPreparationMode.DECOMPOSED,
      )
    }

    val intake = FeatureSpecPreparationIntake(
      issueKey = "SKILL-1",
      intendedOutcome = "outcome",
      acceptanceCriteria = listOf("AC-1"),
      constraints = emptyList(),
      nonGoals = emptyList(),
    )
    val decision = runtime.prepareForFeatureSpec(intake)

    assertEquals(intake, seenIntake)
    assertEquals(FeatureSpecPreparationMode.DECOMPOSED, decision.mode)
  }
}
