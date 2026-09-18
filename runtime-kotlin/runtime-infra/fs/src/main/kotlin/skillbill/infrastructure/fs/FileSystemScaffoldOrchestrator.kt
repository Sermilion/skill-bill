package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.install.scaffold.performScaffoldInstall
import skillbill.infrastructure.fs.install.scaffold.rollbackScaffoldInstallTargets
import skillbill.infrastructure.fs.scaffold.adapters.FileSystemScaffoldRepoValidation
import skillbill.infrastructure.fs.scaffold.adapters.FileSystemScaffoldSourceLoader
import skillbill.infrastructure.fs.scaffold.payload.toRawScaffoldPayload
import skillbill.infrastructure.fs.scaffold.runtime.ScaffoldAdapterSeams
import skillbill.infrastructure.fs.scaffold.runtime.scaffold
import skillbill.infrastructure.fs.scaffold.runtime.scaffoldWithAdapters
import skillbill.scaffold.model.ScaffoldResult
import skillbill.scaffold.model.command.ScaffoldCommandRequest

@Inject
class FileSystemScaffoldOrchestrator(
  private val repoValidation: FileSystemScaffoldRepoValidation,
  private val sourceLoader: FileSystemScaffoldSourceLoader,
) {
  fun scaffold(payload: Map<String, Any?>, dryRun: Boolean): ScaffoldResult =
    scaffoldWithAdapters(payload, dryRun, adapterSeams())

  fun scaffold(request: ScaffoldCommandRequest, dryRun: Boolean): ScaffoldResult =
    scaffold(request.toRawScaffoldPayload(), dryRun)

  private fun adapterSeams(): ScaffoldAdapterSeams = ScaffoldAdapterSeams(
    validateScaffold = { plan, repoRoot -> repoValidation.validateScaffold(plan, repoRoot) },
    optionalBaselineLayers = { payload, repoRoot, newPlatform ->
      repoValidation.optionalBaselineLayers(payload, repoRoot, newPlatform)
    },
    resolveAddonConsumerSkillDirs = { payload, packRoot, pack ->
      sourceLoader.resolveAddonConsumerSkillDirs(payload, packRoot, pack)
    },
    performInstall = { txn, plan, repoRoot -> performScaffoldInstall(txn, plan, repoRoot) },
    rollbackInstallTargets = { txn, errors -> rollbackScaffoldInstallTargets(txn, errors) },
  )
}
