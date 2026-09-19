package skillbill.infrastructure.workflow.filesystem
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.workflow.decomposition.repoRoot
import skillbill.infrastructure.workflow.feature.repoRoot
import skillbill.infrastructure.workflow.featuretask.repoRoot
import skillbill.infrastructure.workflow.git.repository.repoRoot
import skillbill.infrastructure.workflow.git.workflow.input
import skillbill.infrastructure.workflow.git.workflow.repoRoot
import skillbill.infrastructure.workflow.git.workflow.specPath
import skillbill.infrastructure.workflow.review.broker.candidate
import skillbill.infrastructure.workflow.review.broker.input
import skillbill.infrastructure.workflow.review.specialists.checkpoint.candidate
import skillbill.infrastructure.workflow.review.specialists.system.candidate
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.featurespec.FeatureSpecPathResolverPort
import skillbill.ports.featurespec.model.FeatureSpecPathResolveInput
import skillbill.ports.featurespec.model.FeatureSpecPathResolveResult
import java.nio.file.Files
import java.nio.file.Path

@Inject
class FileSystemFeatureSpecPathResolver : FeatureSpecPathResolverPort {
  override fun resolve(input: FeatureSpecPathResolveInput): FeatureSpecPathResolveResult {
    input.explicitSpecPath?.takeIf(String::isNotBlank)?.let { explicit ->
      return FeatureSpecPathResolveResult.Explicit(Path.of(explicit).toString())
    }
    val specsRoot = input.repoRoot.toAbsolutePath().normalize().resolve(".feature-specs")
    if (!Files.isDirectory(specsRoot)) {
      return FeatureSpecPathResolveResult.NoMatch(input.issueKey, specsRoot)
    }
    val matches = Files.list(specsRoot).use { stream ->
      stream
        .filter { candidate -> Files.isDirectory(candidate) }
        .filter { candidate -> candidate.fileName.toString().startsWith("${input.issueKey}-") }
        .map { candidate -> candidate.resolve("spec.md") }
        .filter { specPath -> Files.isRegularFile(specPath) }
        .toList()
        .sorted()
    }
    return when (matches.size) {
      0 -> FeatureSpecPathResolveResult.NoMatch(input.issueKey, specsRoot)
      1 -> FeatureSpecPathResolveResult.SingleMatch(matches.single().toString())
      else -> FeatureSpecPathResolveResult.Ambiguous(input.issueKey, matches)
    }
  }
}
