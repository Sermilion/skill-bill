package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.model.GoalPullRequestRequest
import skillbill.ports.goalrunner.runner.model.GoalPullRequestResult
import skillbill.infrastructure.fs.launcher.process.BoundedExternalProcessRequest
import skillbill.infrastructure.fs.launcher.process.BoundedExternalProcessRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Inject
class GhGoalPullRequestPort() : GoalPullRequestPort {
  private var ghExecutableResolver: (Path) -> Path? = ::resolveGhExecutable

  internal constructor(ghExecutableResolver: (Path) -> Path?) : this() {
    this.ghExecutableResolver = ghExecutableResolver
  }

  override fun open(request: GoalPullRequestRequest): GoalPullRequestResult =
    request.headBranch.takeIf(String::isNotBlank)
      ?.let { head -> openWithHead(request, head) }
      ?: GoalPullRequestResult.Failed("A head branch is required before creating the goal pull request.")

  private fun openWithHead(request: GoalPullRequestRequest, head: String): GoalPullRequestResult {
    val root = request.repoRoot.toAbsolutePath().normalize()
    val existing = runGh(
      root,
      listOf("pr", "list", "--head", head, "--json", "url", "--jq", ".[0].url", "--limit", "1"),
    )
    return if (existing.exitCode == 0 && existing.stdout.trim().startsWith("http")) {
      GoalPullRequestResult.Existing(existing.stdout.trim())
    } else {
      createPullRequest(root, request, head)
    }
  }

  private fun createPullRequest(root: Path, request: GoalPullRequestRequest, head: String): GoalPullRequestResult {
    val create = runGh(root, createArgs(request, head))
    return if (create.exitCode == 0) {
      create.stdout.lineSequence()
        .map(String::trim)
        .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        ?.let(GoalPullRequestResult::Opened)
        ?: GoalPullRequestResult.Failed("Goal pull request was created but no PR URL was returned.")
    } else {
      GoalPullRequestResult.Failed(describeGhFailure(create))
    }
  }

  private fun createArgs(request: GoalPullRequestRequest, head: String): List<String> = listOf(
    "pr",
    "create",
    "--head",
    head,
    "--base",
    request.baseBranch,
    "--draft",
    "--title",
    request.title,
    "--body",
    request.body,
  )

  private fun runGh(root: Path, args: List<String>): CommandResult = runCatching {
    val executable = ghExecutableResolver(root)
      ?: return CommandResult(exitCode = 1, stdout = "GitHub CLI executable was not found on PATH.")
    val result = BoundedExternalProcessRunner.run(
      BoundedExternalProcessRequest(
        argv = listOf(executable.toString()) + args,
        workingDirectory = root,
        mergeEnvironment = mapOf("GIT_TERMINAL_PROMPT" to "0"),
        deadlineSeconds = COMMAND_TIMEOUT_SECONDS,
        outputCapBytes = MAX_OUTPUT_BYTES.toLong(),
      ),
    )
    if (result.timedOut) {
      CommandResult(exitCode = 124, stdout = "GitHub CLI timed out.")
    } else if (result.launchFailure) {
      CommandResult(exitCode = 1, stdout = result.output)
    } else {
      CommandResult(exitCode = result.exitCode, stdout = result.output)
    }
  }.getOrElse { error ->
    CommandResult(
      exitCode = 1,
      stdout = error.message?.let { "${error::class.simpleName}: $it" } ?: (error::class.simpleName ?: "Error"),
    )
  }

  private fun describeGhFailure(result: CommandResult): String {
    val output = result.stdout.trim().replace(Regex("(?i)(https?://)([^\\s/@]+)@")) { match ->
      "${match.groupValues[1]}<redacted>@"
    }
    return if (output.isBlank()) "GitHub provider exited with code ${result.exitCode}." else output
  }

  private fun resolveGhExecutable(root: Path): Path? {
    val names = executableNames("gh")
    val host = JdkHostPlatformPort
    return host.resolveEnvironment()["PATH"]
      .orEmpty()
      .split(host.pathSeparator)
      .asSequence()
      .mapNotNull { raw -> raw.takeIf(String::isNotBlank)?.let(Path::of) }
      .flatMap { directory -> names.asSequence().map(directory::resolve) }
      .firstOrNull { candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate) }
      ?: names.asSequence()
        .map(root::resolve)
        .firstOrNull { candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate) }
  }

  private fun executableNames(base: String): List<String> =
    if (JdkHostPlatformPort.osName.contains("windows", ignoreCase = true)) {
      listOf("$base.exe", "$base.cmd", "$base.bat", base)
    } else {
      listOf(base)
    }
}

private data class CommandResult(
  val exitCode: Int,
  val stdout: String,
)

private const val COMMAND_TIMEOUT_SECONDS: Long = 30
private const val READER_JOIN_TIMEOUT_MILLIS: Long = 1_000
private const val BUFFER_BYTES: Int = 4096
private const val MAX_OUTPUT_BYTES: Int = 64 * 1024
