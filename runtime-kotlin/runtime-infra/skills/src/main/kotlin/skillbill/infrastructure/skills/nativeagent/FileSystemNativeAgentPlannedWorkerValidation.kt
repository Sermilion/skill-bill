package skillbill.infrastructure.skills.nativeagent
import skillbill.infrastructure.skills.externaladdon.map
import skillbill.infrastructure.skills.externaladdon.message
import skillbill.infrastructure.skills.externaladdon.name
import skillbill.infrastructure.skills.externaladdon.slug
import skillbill.infrastructure.skills.file.map
import skillbill.infrastructure.skills.install.manifest
import skillbill.infrastructure.skills.install.size
import skillbill.infrastructure.skills.nativeagent.rendering.discoverRepoNativeAgentSourceFiles
import skillbill.infrastructure.skills.scaffold.path
import skillbill.infrastructure.skills.scaffold.platformpack.loader.discoverPlatformPackManifests
import skillbill.infrastructure.skills.scaffold.skills
import skillbill.review.plan.ReviewLaunchPlanPolicy
import java.nio.file.Files
import java.nio.file.Path

internal fun validatePlannedNativeAgentWorkers(repoRoot: Path): List<String> {
  val root = repoRoot.toAbsolutePath().normalize()
  val packsRoot = root.resolve("platform-packs")
  if (!Files.isDirectory(packsRoot) || !Files.isDirectory(root.resolve("orchestration/contracts"))) {
    return emptyList()
  }
  val issues = mutableListOf<String>()
  val sources = discoverRepoNativeAgentSourceFiles(root).flatMap { path ->
    runCatching { skillbill.infrastructure.skills.nativeagent.composition.parseNativeAgentSourceFile(path) }
      .getOrElse { emptyList() }
  }
  val manifests = runCatching { discoverPlatformPackManifests(packsRoot) }
    .getOrElse { error ->
      return listOf("platform-packs: cannot derive native-agent worker set: ${error.message.orEmpty()}")
    }
  val plannedNames = manifests.flatMap { manifest ->
    val selectedAreas = ReviewLaunchPlanPolicy.composedAreas(manifest.slug, manifests)
    ReviewLaunchPlanPolicy.flatten(manifest.slug, manifests, selectedAreas).lanes.map { it.skillName }
  }.toSortedSet()
  val declarations = sources.groupBy { it.name }
  plannedNames.forEach { worker ->
    when (declarations[worker].orEmpty().size) {
      0 -> issues += "platform-packs: planned review worker '$worker' has no native-agent source declaration"
      1 -> Unit
      else -> issues += "platform-packs: planned review worker '$worker' has ambiguous native-agent declarations"
    }
  }
  return issues.sorted()
}
