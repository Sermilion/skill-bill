package skillbill.ports.install

import skillbill.install.model.InstallPlanWireMap

interface InstallPlanWireValidator {
  fun validate(plan: InstallPlanWireMap)
}
