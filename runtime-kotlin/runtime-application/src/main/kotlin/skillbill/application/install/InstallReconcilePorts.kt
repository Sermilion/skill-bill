package skillbill.application.install

import me.tatarka.inject.annotations.Inject
import skillbill.ports.install.baseline.BaselineManifestPersistencePort
import skillbill.ports.install.reconcile.InstallReconcileApplyPort
import skillbill.ports.install.reconcile.InstallReconcilePort

@Inject
class InstallReconcilePorts(
  val reconcilePort: InstallReconcilePort,
  val reconcileApplyPort: InstallReconcileApplyPort,
  val baselineManifestPersistencePort: BaselineManifestPersistencePort,
)
