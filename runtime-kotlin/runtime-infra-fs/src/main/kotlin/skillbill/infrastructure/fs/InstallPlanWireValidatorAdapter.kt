package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.install.InstallPlanSchemaValidator
import skillbill.install.model.InstallPlanWireMap
import skillbill.install.model.InstallPlanWireValidator

@Inject
class InstallPlanWireValidatorAdapter : InstallPlanWireValidator {
  override fun validate(plan: InstallPlanWireMap) {
    InstallPlanSchemaValidator.validate(plan)
  }
}
