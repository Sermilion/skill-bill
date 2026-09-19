package skillbill.engine.featuretask.validation

import me.tatarka.inject.annotations.Inject
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort
import skillbill.ports.validation.PrCheckDiscovery
import skillbill.ports.validation.model.PrCheckDiscoveryResult
import skillbill.review.plan.ReviewPathMatcher
import skillbill.review.plan.ReviewStackRouting
import skillbill.review.plan.model.ReviewRoutingChangedFile
import skillbill.review.plan.model.ReviewStackRoutingResult
import skillbill.scaffold.model.PlatformManifest
import skillbill.scaffold.model.ValidationGateDeclaration
import java.nio.file.Path

const val READINESS_PACK_COLLECT_ALL_CHECK_ID: String = "pack-collect-all"

data class ReadinessSelectedCheck(
  val checkId: String,
  val command: String,
  val pathPatterns: List<String>,
  val gateArgv: List<String>? = null,
  val gateDeclaration: ValidationGateDeclaration? = null,
)

sealed interface ReadinessCheckSelectionResult {
  data class Selected(val checks: List<ReadinessSelectedCheck>) : ReadinessCheckSelectionResult

  data class Failed(val reason: String) : ReadinessCheckSelectionResult
}

@Inject
class ReadinessCheckSelection(
  private val installedCatalog: InstalledPlatformPackCatalogPort,
  private val prCheckDiscovery: PrCheckDiscovery,
) {
  fun select(repoRoot: Path, changedPaths: List<String>): ReadinessCheckSelectionResult {
    val sourcePaths = changedPaths.filterNot(ReadinessPathRules::isBoundaryHistoryPath)
    val discovery = prCheckDiscovery.discoverPullRequestChecks(repoRoot)
    if (discovery is PrCheckDiscoveryResult.Failed) {
      return ReadinessCheckSelectionResult.Failed(discovery.reason)
    }
    val workflowChecks = (discovery as PrCheckDiscoveryResult.Discovered).checks
    val selected = mutableListOf<ReadinessSelectedCheck>()
    workflowChecks.forEach { check ->
      if (sourcePaths.any { path -> ReadinessPathRules.matchesAnyFilter(path, check.pathPatterns) }) {
        selected += ReadinessSelectedCheck(check.checkId, check.command, check.pathPatterns)
      }
    }
    val packCheck = packCollectAllCheck(sourcePaths)
    if (packCheck != null) {
      selected += packCheck
    }
    return ReadinessCheckSelectionResult.Selected(selected.distinctBy(ReadinessSelectedCheck::checkId))
  }

  fun invalidatedCheckIds(selected: List<ReadinessSelectedCheck>, changedPaths: List<String>): Set<String> {
    val sourcePaths = changedPaths.filterNot(ReadinessPathRules::isBoundaryHistoryPath)
    return selected.filter { check ->
      sourcePaths.any { path -> ReadinessPathRules.matchesAnyFilter(path, check.pathPatterns) }
    }.map(ReadinessSelectedCheck::checkId).toSet()
  }

  private fun packCollectAllCheck(sourcePaths: List<String>): ReadinessSelectedCheck? =
    sourcePaths.takeIf(List<String>::isNotEmpty)
      ?.let { paths ->
        installedCatalog.manifests().takeIf(List<PlatformManifest>::isNotEmpty)?.let { manifests ->
          val routing = ReviewStackRouting.route(
            manifests,
            paths.map { ReviewRoutingChangedFile(it, "") },
          )
          dominantRoutedPack(manifests, routing)?.let { dominant ->
            dominant.validationGate?.let { gate ->
              val argv = gate.collectAllFullGateCommand
              val patterns = (dominant.routingSignals.path + dominant.routingSignals.strong).distinct()
              argv.takeIf(List<String>::isNotEmpty)
                ?.takeIf { patterns.isNotEmpty() }
                ?.let {
                  ReadinessSelectedCheck(
                    checkId = READINESS_PACK_COLLECT_ALL_CHECK_ID,
                    command = argv.joinToString(" "),
                    pathPatterns = patterns,
                    gateArgv = argv,
                    gateDeclaration = gate,
                  )
                }
            }
          }
        }
      }

  private fun dominantRoutedPack(
    manifests: List<PlatformManifest>,
    routing: ReviewStackRoutingResult,
  ): PlatformManifest? {
    if (routing.routedSlugs.isEmpty()) return null
    val bySlug = manifests.associateBy { it.slug }
    val routed = routing.routedSlugs.mapNotNull(bySlug::get)
    val gated = routed.filter { it.validationGate != null }
    if (gated.isEmpty()) return null
    return gated.maxByOrNull { pack -> routing.ownedPathsBySlug[pack.slug]?.size ?: 0 }
  }
}

object ReadinessPathRules {
  fun isBoundaryHistoryPath(path: String): Boolean =
    path.endsWith("agent/history.md") || path.startsWith(".skill-bill/run-evidence/")

  fun matchesAnyFilter(path: String, patterns: List<String>): Boolean =
    patterns.any { pattern -> matchesFilter(path, pattern) }

  fun matchesFilter(path: String, pattern: String): Boolean {
    val normalizedPath = path.replace('\\', '/').trimStart('/')
    val normalizedPattern = pattern.replace('\\', '/').trimStart('/')
    return ReviewPathMatcher.matches(normalizedPath, normalizedPattern)
  }
}
