package skillbill.engine.featuretask

import me.tatarka.inject.annotations.Inject
import skillbill.featurespec.model.FeatureSpecPreparationDecision
import skillbill.featurespec.model.FeatureSpecPreparationIntake

@Inject
class FeatureSpecPreparationRuntime(
  private val prepareCore: (FeatureSpecPreparationIntake) -> FeatureSpecPreparationDecision,
) {
  fun prepareForFeatureSpec(intake: FeatureSpecPreparationIntake): FeatureSpecPreparationDecision = prepareCore(intake)
}
