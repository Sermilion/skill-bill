package skillbill.engine.experiment.navigation

import me.tatarka.inject.annotations.Inject
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.ports.experiment.navigation.ExperimentNavigationDecisionAdapter
import skillbill.ports.experiment.navigation.ExperimentNavigationDecisionContext
import skillbill.ports.experiment.navigation.ExperimentNavigationLabelCoverage
import skillbill.ports.experiment.navigation.ExperimentNavigationReadReceipt
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionResult
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRunnerPort
import skillbill.ports.experiment.navigation.ExperimentNavigationTerminalOutcome
import skillbill.ports.experiment.navigation.model.ExperimentNavigationDecision
import java.nio.file.Files
import java.nio.file.Path

private const val MINIMUM_CRITERION_TERM_LENGTH = 4

@Inject
class BoundedReadOnlyExperimentNavigationSessionRunner(
  private val decisionAdapter: ExperimentNavigationDecisionAdapter,
) : ExperimentNavigationSessionRunnerPort {
  override fun runSession(request: ExperimentNavigationSessionRequest): ExperimentNavigationSessionResult {
    val root = request.repoRoot.toAbsolutePath().normalize()
    if (!Files.isDirectory(root)) {
      throw ExperimentIsolationCapabilityRefusalError("Navigation snapshot does not exist: $root")
    }
    val receipts = mutableListOf<ExperimentNavigationReadReceipt>()
    val delivered = linkedSetOf<String>()
    val shortlisted = linkedSetOf<String>()
    var satisfied = 0
    val decisions = decisionAdapter.decide(
      ExperimentNavigationDecisionContext(
        pairId = request.pairId,
        armId = request.armId,
        repoRoot = root,
        acceptanceCriteria = request.acceptanceCriteria,
      ),
    )
    for (decision in decisions) {
      when (decision) {
        is ExperimentNavigationDecision.Search -> satisfied += search(
          root,
          decision.query,
          receipts,
          delivered,
          shortlisted,
          request.acceptanceCriteria,
        )
        is ExperimentNavigationDecision.Read -> {
          val path = root.resolve(decision.path).normalize()
          if (!Files.isRegularFile(path) || !isAllowed(root, path)) {
            throw ExperimentIsolationCapabilityRefusalError(
              "Navigation decision attempted to read an unavailable snapshot path.",
            )
          }
          val relative = root.relativize(path).toString().replace('\\', '/')
          val text = readText(path)
          if (text != null) {
            request.acceptanceCriteria.forEach { criterion ->
              if (criterionTerms(criterion).any { term -> text.contains(term, ignoreCase = true) }) {
                satisfied += 1
                delivered += relative
              }
            }
            shortlisted += relative
            receipts += ExperimentNavigationReadReceipt(relative, "direct_read")
          }
        }
        ExperimentNavigationDecision.Complete -> break
      }
    }
    val uniqueSatisfied = satisfied.coerceAtMost(request.acceptanceCriteria.size)
    return ExperimentNavigationSessionResult(
      outcome = if (uniqueSatisfied == request.acceptanceCriteria.size) {
        ExperimentNavigationTerminalOutcome.SEARCH_COMPLETED
      } else {
        ExperimentNavigationTerminalOutcome.INSUFFICIENT_EVIDENCE
      },
      deliveredPaths = delivered.toList(),
      shortlistedPaths = shortlisted.toList(),
      readReceipts = receipts,
      attemptCount = request.acceptanceCriteria.size,
      labelCoverage = ExperimentNavigationLabelCoverage(
        labelledCriteria = 0,
        totalCriteria = request.acceptanceCriteria.size,
        precisionAvailable = false,
      ),
      excludedPaths = listOf(".git", ".skill-bill", ".skill-bill-experiments")
        .filter { name -> Files.exists(root.resolve(name)) },
      restrictedBaseline = true,
    )
  }

  private fun search(
    root: Path,
    query: String,
    receipts: MutableList<ExperimentNavigationReadReceipt>,
    delivered: MutableSet<String>,
    shortlisted: MutableSet<String>,
    criteria: List<String>,
  ): Int {
    var satisfied = 0
    Files.walk(root).use { paths ->
      paths
        .filter(Files::isRegularFile)
        .filter { path -> isAllowed(root, path) }
        .forEach { path ->
          val text = readText(path) ?: return@forEach
          if (!text.contains(query, ignoreCase = true) &&
            !path.fileName.toString().contains(query, ignoreCase = true)
          ) {
            return@forEach
          }
          val relative = root.relativize(path).toString().replace('\\', '/')
          receipts += ExperimentNavigationReadReceipt(relative, "search")
          shortlisted += relative
          criteria.forEach { criterion ->
            if (criterionTerms(criterion).any { term -> text.contains(term, ignoreCase = true) }) {
              satisfied += 1
              delivered += relative
            }
          }
        }
    }
    return satisfied
  }

  private fun isAllowed(root: Path, path: Path): Boolean {
    val normalized = path.toAbsolutePath().normalize()
    if (!normalized.startsWith(root)) {
      throw ExperimentIsolationCapabilityRefusalError("Navigation attempted to escape its snapshot.")
    }
    val segments = root.relativize(normalized).map(Path::toString)
    return segments.none { segment ->
      segment == ".git" ||
        segment == ".skill-bill" ||
        segment.startsWith(".skill-bill-") ||
        segment.contains("benchmark", ignoreCase = true) ||
        segment.contains("secret", ignoreCase = true)
    }
  }

  private fun readText(path: Path): String? = runCatching { Files.readString(path) }.getOrNull()

  private fun criterionTerms(criterion: String): List<String> = criterion
    .split(Regex("[^A-Za-z0-9_]+"))
    .map(String::trim)
    .filter { it.length >= MINIMUM_CRITERION_TERM_LENGTH }
    .distinct()
}
