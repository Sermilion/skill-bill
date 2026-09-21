package skillbill.engine.experiment.codegraph

import skillbill.ports.experiment.codegraph.CodeGraphPairSetupPort
import skillbill.ports.experiment.codegraph.CodeGraphToolInstallPort
import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
import skillbill.ports.experiment.codegraph.model.CodeGraphPairSetupRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphPairSetupResult
import skillbill.ports.experiment.codegraph.model.CodeGraphToolInstallRequest

private const val CODEGRAPH_EXPERIMENT_NAME: String = "codegraph"
private const val NANOS_PER_MILLISECOND: Long = 1_000_000L

class CodeGraphPairSetupService(
  private val toolInstallPort: CodeGraphToolInstallPort,
  private val usageLedger: CodeGraphUsageLedgerPort,
) : CodeGraphPairSetupPort {
  override fun provisionAfterConfirmation(request: CodeGraphPairSetupRequest): CodeGraphPairSetupResult {
    if (CODEGRAPH_EXPERIMENT_NAME !in request.selectedExperimentNames) {
      return CodeGraphPairSetupResult(provisioned = false, reason = "codegraph was not selected")
    }
    val startedAt = System.nanoTime()
    return try {
      val installed = toolInstallPort.install(
        CodeGraphToolInstallRequest(
          userHome = request.userHome,
          pairId = request.pairId,
          pinnedReleaseTag = request.pinnedReleaseTag,
          localExecutableOverride = request.localExecutableOverride,
        ),
      )
      CodeGraphPairSetupResult(
        provisioned = true,
        installedTool = installed,
        installDurationMs = elapsedMillis(startedAt),
      )
    } finally {
      usageLedger.recordSetup(request.pairId, elapsedMillis(startedAt))
    }
  }

  override fun releaseOwnedResources(pairId: String) {
    toolInstallPort.releaseOwnedProcesses(pairId)
  }

  private fun elapsedMillis(startedAt: Long): Long =
    ((System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)
}
