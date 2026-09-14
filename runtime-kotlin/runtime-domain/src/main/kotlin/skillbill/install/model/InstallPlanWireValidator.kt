package skillbill.install.model

import skillbill.error.InvalidInstallPlanSchemaError

interface InstallPlanWireValidator {
  fun validate(plan: InstallPlanWireMap)
}
