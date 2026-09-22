package skillbill.infrastructure.skills.install.nativeagent.install.native
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentDeclaredFiles
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentGovernedAddonActivation
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentGovernedAddonSelection
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentGovernedAddonUsage
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentPlatformPack
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentPlatformPackLoader
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentPointerSpec
import skillbill.infrastructure.skills.scaffold.platformpack.loader.loadPlatformManifest
import skillbill.model.toPath
import skillbill.scaffold.model.GovernedAddonActivation
import skillbill.scaffold.model.GovernedAddonSelection
import skillbill.scaffold.model.GovernedAddonUsage
import skillbill.scaffold.model.PlatformManifest
import skillbill.scaffold.model.PointerSpec
import java.nio.file.Path
import skillbill.infrastructure.skills.scaffold.platformpack.loader.discoverPlatformPackManifests as scaffoldDiscoverPlatformPackManifests
import skillbill.infrastructure.skills.scaffold.platformpack.loader.loadPlatformPack as scaffoldLoadPlatformPack

internal object InstallNativeAgentPlatformPackLoader : NativeAgentPlatformPackLoader {
  override fun loadPlatformPack(packRoot: Path, additionalPackRoots: List<Path>): NativeAgentPlatformPack {
    if (additionalPackRoots.isEmpty()) {
      return scaffoldLoadPlatformPack(packRoot).toNativeAgentPlatformPack()
    }
    val manifests = additionalPackRoots.map { root -> loadPlatformManifest(root.toAbsolutePath().normalize()) }
    return scaffoldLoadPlatformPack(packRoot, manifests.associateBy { manifest -> manifest.slug })
      .toNativeAgentPlatformPack()
  }

  override fun discoverPlatformPackManifests(platformPacksRoot: Path): List<NativeAgentPlatformPack> =
    scaffoldDiscoverPlatformPackManifests(platformPacksRoot).map(PlatformManifest::toNativeAgentPlatformPack)
}

internal fun PlatformManifest.toNativeAgentPlatformPack(): NativeAgentPlatformPack = NativeAgentPlatformPack(
  slug = slug,
  packRoot = packRoot.toPath(),
  declaredFiles = NativeAgentDeclaredFiles(
    baseline = declaredFiles.baseline?.toPath(),
    areas = declaredFiles.areas.mapValues { (_, entry) -> entry.toPath() },
  ),
  pointers = pointers.map(PointerSpec::toNativeAgentPointerSpec),
  addonUsage = addonUsage.map(GovernedAddonUsage::toNativeAgentGovernedAddonUsage),
)

private fun PointerSpec.toNativeAgentPointerSpec(): NativeAgentPointerSpec = NativeAgentPointerSpec(
  skillRelativeDir = skillRelativeDir,
  name = name,
  target = target,
)

private fun GovernedAddonUsage.toNativeAgentGovernedAddonUsage(): NativeAgentGovernedAddonUsage =
  NativeAgentGovernedAddonUsage(
    skillRelativeDir = skillRelativeDir,
    addons = addons.map(GovernedAddonSelection::toNativeAgentGovernedAddonSelection),
  )

private fun GovernedAddonSelection.toNativeAgentGovernedAddonSelection(): NativeAgentGovernedAddonSelection =
  NativeAgentGovernedAddonSelection(
    slug = slug,
    entrypoint = entrypoint,
    companionPointers = companionPointers,
    activation = activation?.toNativeAgentGovernedAddonActivation(),
    specialistAreas = specialistAreas,
  )

private fun GovernedAddonActivation.toNativeAgentGovernedAddonActivation(): NativeAgentGovernedAddonActivation =
  NativeAgentGovernedAddonActivation(
    anyPath = anyPath,
    anyContent = anyContent,
    allContent = allContent,
    anyOfAllContent = anyOfAllContent,
    excludePath = excludePath,
    excludeContent = excludeContent,
  )
