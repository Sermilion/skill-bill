package skillbill.engine.featuretask.validation

import me.tatarka.inject.annotations.Inject
import skillbill.ports.validation.PrCheckDiscovery
import skillbill.ports.validation.model.PrCheckDiscoveryResult
import skillbill.review.plan.ReviewPathMatcher
import java.nio.file.Path

const val READINESS_PACK_COLLECT_ALL_CHECK_ID: String = "pack-collect-all"

data class ReadinessSelectedCheck(
  val checkId: String,
  val command: String,
  val pathPatterns: List<String>,
)

sealed interface ReadinessCheckSelectionResult {
  data class Selected(val checks: List<ReadinessSelectedCheck>) : ReadinessCheckSelectionResult

  data class Failed(val reason: String) : ReadinessCheckSelectionResult
}

@Inject
class ReadinessCheckSelection(
  private val prCheckDiscovery: PrCheckDiscovery,
) {
  fun select(
    repoRoot: Path,
    changedPaths: List<String>,
  ): ReadinessCheckSelectionResult {
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
    return ReadinessCheckSelectionResult.Selected(selected.distinctBy(ReadinessSelectedCheck::checkId))
  }

  fun invalidatedCheckIds(
    selected: List<ReadinessSelectedCheck>,
    changedPaths: List<String>,
  ): Set<String> {
    val sourcePaths = changedPaths.filterNot(ReadinessPathRules::isBoundaryHistoryPath)
    return selected.filter { check ->
      sourcePaths.any { path -> ReadinessPathRules.matchesAnyFilter(path, check.pathPatterns) }
    }.map(ReadinessSelectedCheck::checkId).toSet()
  }
}

object ReadinessPathRules {
  fun isBoundaryHistoryPath(path: String): Boolean =
    path.endsWith("agent/history.md") || path.startsWith(".skill-bill/run-evidence/")

  fun matchesAnyFilter(
    path: String,
    patterns: List<String>,
  ): Boolean = patterns.any { pattern -> matchesFilter(path, pattern) }

  fun matchesFilter(
    path: String,
    pattern: String,
  ): Boolean {
    val normalizedPath = path.replace('\\', '/').trimStart('/')
    val normalizedPattern = pattern.replace('\\', '/').trimStart('/')
    return ReviewPathMatcher.matches(normalizedPath, normalizedPattern)
  }
}
