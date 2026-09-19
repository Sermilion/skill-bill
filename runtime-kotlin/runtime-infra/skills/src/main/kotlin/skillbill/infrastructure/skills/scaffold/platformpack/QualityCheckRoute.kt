package skillbill.infrastructure.skills.scaffold.platformpack

import skillbill.error.MissingValidationGateError
import skillbill.scaffold.model.PlatformManifest
import java.nio.file.Path

internal data class QualityCheckRoute(
  val detectedStack: String,
  val routedSkill: String,
  val matchedSignals: List<String>,
  val fallback: Boolean = false,
  val fallbackReason: String? = null,
)

internal fun routeQualityCheck(repoRoot: Path, routingEvidence: Collection<String>): QualityCheckRoute? {
  val packs = discoverPlatformPacks(repoRoot.toAbsolutePath().normalize().resolve("platform-packs"))
  val candidates = packs.map { pack ->
    val matched = pack.routingSignals.strong.filter { signal ->
      routingEvidence.any { evidence -> routingSignalMatches(evidence, signal) }
    }.distinct()
    RoutingCandidate(pack, matched)
  }.filter { it.matchedSignals.isNotEmpty() }
  if (candidates.isEmpty()) return null
  val adjacentWinner = resolveKotlinKmpDominance(candidates, routingEvidence)
  val rankedCandidates = adjacentWinner?.let { selected ->
    candidates.filterNot { it.pack.slug == "kotlin" || it.pack.slug == "kmp" } + selected
  } ?: candidates
  val maximum = rankedCandidates.maxOf(RoutingCandidate::rank)
  val winners = rankedCandidates.filter { it.rank() == maximum }
  val winner = winners.singleOrNull()
  require(winner != null) {
    val slugs = winners.map { it.pack.slug }.sorted()
    "Quality-check routing is ambiguous for $slugs; provide stronger manifest-declared evidence."
  }
  val pack = winner.pack
  if (pack.validationGate == null) {
    throw MissingValidationGateError(
      "Platform pack '${pack.slug}' has no validation_gate declaration; quality-check cannot run.",
    )
  }
  return QualityCheckRoute(pack.slug, QUALITY_CHECK_SHELL_SKILL, winner.matchedSignals.sorted())
}

private const val QUALITY_CHECK_SHELL_SKILL = "bill-code-check"

private data class RoutingRank(
  val ownershipSignals: Int,
  val maximumSpecificity: Int,
  val totalSpecificity: Int,
  val matchedSignals: Int,
) : Comparable<RoutingRank> {
  override fun compareTo(other: RoutingRank): Int = compareValuesBy(
    this,
    other,
    RoutingRank::ownershipSignals,
    RoutingRank::maximumSpecificity,
    RoutingRank::totalSpecificity,
    RoutingRank::matchedSignals,
  )
}

private data class RoutingCandidate(
  val pack: PlatformManifest,
  val matchedSignals: List<String>,
) {
  fun rank(): RoutingRank {
    val specificities = matchedSignals.map(::signalSpecificity)
    return RoutingRank(
      matchedSignals.count { !isGenericLanguageSignal(it) },
      specificities.maxOrNull() ?: 0,
      specificities.sum(),
      matchedSignals.size,
    )
  }
}

private fun resolveKotlinKmpDominance(
  candidates: List<RoutingCandidate>,
  routingEvidence: Collection<String>,
): RoutingCandidate? {
  val kotlin = candidates.singleOrNull { it.pack.slug == "kotlin" } ?: return null
  val kmp = candidates.singleOrNull { it.pack.slug == "kmp" } ?: return null
  val kmpOwnedEvidence = routingEvidence.filter(::hasKmpOwnership)
  val kotlinOwnedEvidence = routingEvidence.filter { evidence ->
    !hasKmpOwnership(evidence) && KOTLIN_OWNERSHIP_SIGNALS.any { routingSignalMatches(evidence, it) }
  }
  return when {
    kmpOwnedEvidence.isNotEmpty() && kotlinOwnedEvidence.isEmpty() -> kmp
    kotlinOwnedEvidence.isNotEmpty() && kmpOwnedEvidence.isEmpty() -> kotlin
    kmpOwnedEvidence.isEmpty() && kotlinOwnedEvidence.isEmpty() -> kotlin
    else -> kmp
  }
}

private fun hasKmpOwnership(evidence: String): Boolean = KMP_OWNERSHIP_SIGNALS.any {
  routingSignalMatches(evidence, it)
}

private fun routingSignalMatches(evidence: String, signal: String): Boolean {
  val normalizedEvidence = evidence.lowercase().replace('\\', '/')
  val normalizedSignal = signal.lowercase().replace('\\', '/')
  val basename = normalizedEvidence.substringAfterLast('/')
  return when {
    '*' in normalizedSignal -> {
      val pattern = normalizedSignal.split('*').joinToString(".*") { part -> Regex.escape(part) }
      val regex = Regex("^$pattern$")
      regex.matches(normalizedEvidence) || regex.matches(basename)
    }
    normalizedSignal.startsWith(".") && '/' !in normalizedSignal -> basename.endsWith(normalizedSignal)
    normalizedSignal.endsWith('/') -> {
      val segment = Regex("(?:^|/)${Regex.escape(normalizedSignal.removeSuffix("/"))}/")
      segment.containsMatchIn(normalizedEvidence)
    }
    '/' in normalizedSignal ->
      normalizedEvidence == normalizedSignal || normalizedEvidence.endsWith("/$normalizedSignal")
    normalizedEvidence.split('/').any { it == normalizedSignal } -> true
    else -> {
      val lexical = Regex("(?<![\\p{L}\\p{N}_])${Regex.escape(normalizedSignal)}(?![\\p{L}\\p{N}_])")
      lexical.containsMatchIn(normalizedEvidence)
    }
  }
}

private fun signalSpecificity(signal: String): Int = signal.count { it.isLetterOrDigit() }

private fun isGenericLanguageSignal(signal: String): Boolean = signal.lowercase() in setOf(
  ".kt", "*.kt", ".kts", "*.kts", ".ts", "*.ts", ".tsx", "*.tsx", ".mts", "*.mts", ".cts", "*.cts",
  ".py", "*.py", ".php", "*.php", ".rs", "*.rs", ".go", "*.go", ".swift", "*.swift",
)

private val KMP_OWNERSHIP_SIGNALS = listOf(
  "commonMain",
  "androidMain",
  "iosMain",
  "org.jetbrains.kotlin.multiplatform",
  "kotlin(\"multiplatform\")",
  "expect",
  "actual",
  "AndroidManifest.xml",
)

private val KOTLIN_OWNERSHIP_SIGNALS = listOf(
  "build.gradle",
  "build.gradle.kts",
  "settings.gradle.kts",
  "gradle/libs.versions.toml",
  "detekt.yml",
  "kotlin/",
)
