package skillbill.infrastructure.skills.install
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.skills.install.reconcile.ReconcileSourceRoots
import skillbill.infrastructure.skills.install.reconcile.ReconcileSourceSide
import skillbill.infrastructure.skills.install.reconcile.enumerateSkills
import skillbill.ports.install.baseline.BaselineManifestPersistencePort
import skillbill.ports.install.baseline.InstalledWorkspaceBaselineStatusPort
import skillbill.ports.install.baseline.model.InstalledWorkspaceBaselineStatusRequest
import skillbill.ports.install.baseline.model.InstalledWorkspaceBaselineStatusResult
import skillbill.ports.install.baseline.model.ReadBaselineManifestRequest

@Inject
class FileSystemInstalledWorkspaceBaselineStatus(
  private val baselinePersistence: BaselineManifestPersistencePort,
) : InstalledWorkspaceBaselineStatusPort {

  override fun modifiedSkillRelativePaths(
    request: InstalledWorkspaceBaselineStatusRequest,
  ): InstalledWorkspaceBaselineStatusResult {
    val installRoot = request.installRoot.toAbsolutePath().normalize()
    val read = baselinePersistence.readBaseline(ReadBaselineManifestRequest(installHome = request.installHome))
    if (!read.existed) return InstalledWorkspaceBaselineStatusResult(emptySet())
    val baseline = read.manifest

    val roots = ReconcileSourceRoots(
      repoRoot = installRoot,
      skillsRoot = installRoot.resolve("skills"),
      platformPacksRoot = installRoot.resolve("platform-packs"),
    )
    val live = enumerateSkills(roots, home = request.installHome, sourceSide = ReconcileSourceSide.LOCAL)
    val modified = live.asSequence()
      .filter { (skillRelativePath, entry) -> baseline.hashFor(skillRelativePath) != entry.hash }
      .map { (skillRelativePath, _) -> skillRelativePath }
      .toSet()
    return InstalledWorkspaceBaselineStatusResult(modified)
  }
}
