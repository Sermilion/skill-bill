package skillbill.infrastructure.workflow.github

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import me.tatarka.inject.annotations.Inject
import skillbill.ports.validation.PrCheckDiscovery
import skillbill.ports.validation.model.DiscoveredPrCheck
import skillbill.ports.validation.model.PrCheckDiscoveryResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

@Inject
class GitHubPullRequestCheckDiscovery : PrCheckDiscovery {
  override fun discoverPullRequestChecks(repoRoot: Path): PrCheckDiscoveryResult {
    val workflowsDir = repoRoot.resolve(".github/workflows")
    return if (!Files.isDirectory(workflowsDir)) {
      PrCheckDiscoveryResult.Discovered(emptyList())
    } else {
      runCatching {
        val files = workflowsDir.toFile().listFiles()
          ?: error("Could not read governed workflow directory '$workflowsDir'.")
        val mapper = YAMLMapper()
        files.asSequence()
          .filter { it.toPath().isRegularFile() && it.toPath().extension in WORKFLOW_EXTENSIONS }
          .sortedBy { it.name }
          .mapNotNull { file ->
            val parsed = mapper.readTree(file.readText())
            discoverFromWorkflow(file.nameWithoutExtension, parsed)
          }
          .toList()
      }.fold(
        onSuccess = { checks -> PrCheckDiscoveryResult.Discovered(checks.distinctBy(DiscoveredPrCheck::checkId)) },
        onFailure = { error -> PrCheckDiscoveryResult.Failed(error.message.orEmpty()) },
      )
    }
  }

  private fun discoverFromWorkflow(workflowSlug: String, root: JsonNode): DiscoveredPrCheck? {
    val pathsNode = workflowEvents(root).path("pull_request").path("paths")
    if (!pathsNode.isArray || pathsNode.isEmpty) return null
    val pathPatterns = pathsNode.mapNotNull { node -> node.asText(null)?.trim()?.takeIf(String::isNotBlank) }
    if (pathPatterns.isEmpty()) return null
    val jobs = root.path("jobs")
    require(jobs.isObject) { "pull_request workflow with paths must declare jobs." }
    jobs.fields().forEach { entry ->
      val jobName = entry.key
      val job = entry.value
      if (!jobEligibleForPullRequest(job)) return@forEach
      val command = firstRunCommand(job) ?: return@forEach
      if (command.contains("verifyPlugin") && !command.contains("check")) return@forEach
      return DiscoveredPrCheck(
        checkId = "$workflowSlug:$jobName",
        command = command,
        pathPatterns = pathPatterns,
      )
    }
    error("pull_request workflow with paths has no eligible check job.")
  }

  private fun workflowEvents(root: JsonNode): JsonNode = root.fields().asSequence()
    .firstOrNull { (key, _) -> key == "on" || key == "true" }
    ?.value
    ?: root.path("on")

  private fun jobEligibleForPullRequest(job: JsonNode): Boolean {
    val condition = job.path("if").asText("").trim()
    if (condition.contains("== 'schedule'") || condition.contains("== \"schedule\"")) return false
    return true
  }

  private fun firstRunCommand(job: JsonNode): String? {
    val steps = job.path("steps")
    if (!steps.isArray) return null
    steps.forEach { step ->
      val run = step.path("run").asText("").trim()
      if (run.isNotBlank()) return run
    }
    return null
  }

  companion object {
    private val WORKFLOW_EXTENSIONS = setOf("yml", "yaml")
  }
}
