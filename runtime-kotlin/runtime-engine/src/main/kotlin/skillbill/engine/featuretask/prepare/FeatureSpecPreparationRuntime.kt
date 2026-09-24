package skillbill.engine.featuretask.prepare

import me.tatarka.inject.annotations.Inject
import skillbill.featurespec.FeatureSpecPreparationPolicy
import skillbill.featurespec.model.FeatureSpecPreparationDecision
import skillbill.featurespec.model.FeatureSpecPreparationIntake

@Inject
class FeatureSpecPreparationRuntime {
  fun prepareForFeatureSpec(intake: FeatureSpecPreparationIntake): FeatureSpecPreparationDecision =
    FeatureSpecPreparationPolicy.prepare(intake)
}
