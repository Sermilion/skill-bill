package skillbill.infrastructure.workflow.decomposition
import skillbill.infrastructure.workflow.feature.repoRoot
import skillbill.infrastructure.workflow.featuretask.repoRoot
import skillbill.infrastructure.workflow.filesystem.path
import skillbill.infrastructure.workflow.git.repository.repoRoot
import skillbill.infrastructure.workflow.git.scoped.paths
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.standard.paths
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.paths
import skillbill.infrastructure.workflow.git.workflow.repoRoot
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.specialists.system.directory
import skillbill.infrastructure.workflow.review.specialists.system.path
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.workflow.decomposition.DecompositionManifestDiscoveryPort
import java.nio.file.Files
import java.nio.file.Path

internal class FileSystemDecompositionManifestFileStoreDiscovery(
  private val bundleJournal: DecompositionManifestBundleJournal,
) : DecompositionManifestDiscoveryPort {
  override fun listDirectChildDirectories(directory: Path): List<Path> {
    if (!Files.isDirectory(directory)) return emptyList()
    return Files.list(directory).use { paths ->
      paths.filter { path -> Files.isDirectory(path) }.toList()
    }
  }

  override fun findDecompositionManifestFiles(repoRoot: Path): List<Path> {
    val featureSpecsRoot = repoRoot.resolve(".feature-specs")
    if (!Files.isDirectory(featureSpecsRoot)) return emptyList()
    Files.walk(featureSpecsRoot).use { paths ->
      paths.filter { path -> Files.isDirectory(path) }.forEach(bundleJournal::recoverPending)
    }
    return Files.walk(featureSpecsRoot).use { paths ->
      paths
        .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == "decomposition-manifest.yaml" }
        .toList()
    }
  }

  override fun findDecompositionManifestFilesWithoutRecovery(repoRoot: Path): List<Path> {
    val featureSpecsRoot = repoRoot.resolve(".feature-specs")
    if (!Files.isDirectory(featureSpecsRoot)) return emptyList()
    bundleJournal.failIfPendingUnder(featureSpecsRoot)
    return Files.walk(featureSpecsRoot).use { paths ->
      paths
        .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == "decomposition-manifest.yaml" }
        .toList()
    }
  }
}
