package dev.skillbill.intellij.infrastructure.cli

import dev.skillbill.intellij.application.GoalMutationOutcome
import dev.skillbill.intellij.application.GoalMutationRepository
import dev.skillbill.intellij.application.PreferenceCachePort
import dev.skillbill.intellij.domain.DEFAULT_CLI_TIMEOUT_MS
import dev.skillbill.intellij.domain.DEFAULT_STDERR_LIMIT_BYTES
import dev.skillbill.intellij.domain.DEFAULT_STDOUT_LIMIT_BYTES
import dev.skillbill.intellij.domain.GOAL_PAUSE_VERB
import dev.skillbill.intellij.domain.GOAL_STOP_VERB
import dev.skillbill.intellij.domain.REPO_ROOT_OPTION
import java.nio.file.Path

enum class GoalMutation(
    val verb: List<String>,
    val blankKeySummary: String,
    val launchFailureSummary: String,
    val cancelledSummary: String,
    val timedOutSummary: String,
    val declinedSummary: String,
) {
    PAUSE(
        verb = GOAL_PAUSE_VERB,
        blankKeySummary = "No issue key to pause",
        launchFailureSummary = "Pause request failed to start",
        cancelledSummary = "Pause request cancelled",
        timedOutSummary = "Pause request timed out",
        declinedSummary = "Skill Bill declined the pause request",
    ),
    STOP(
        verb = GOAL_STOP_VERB,
        blankKeySummary = "No issue key to stop",
        launchFailureSummary = "Stop request failed to start",
        cancelledSummary = "Stop request cancelled",
        timedOutSummary = "Stop request timed out",
        declinedSummary = "Skill Bill declined the stop request",
    ),
}

class CliGoalMutationRepository(
    private val mutation: GoalMutation,
    private val preferences: PreferenceCachePort,
    private val processRunner: ProcessRunner,
    private val executableResolver: () -> CliExecutableResolution = {
        CliExecutableResolver.resolve(preferences)
    },
    private val timeoutMs: Long = DEFAULT_CLI_TIMEOUT_MS,
) : GoalMutationRepository {
    override suspend fun requestMutation(projectRoot: Path, issueKey: String): GoalMutationOutcome {
        val key = issueKey.trim()
        if (key.isEmpty()) return GoalMutationOutcome.Failed(mutation.blankKeySummary)
        val executable = when (val resolution = executableResolver()) {
            is CliExecutableResolution.Found -> resolution.path
            CliExecutableResolution.Missing -> return GoalMutationOutcome.Failed("Skill Bill CLI executable not found")
            CliExecutableResolution.Misconfigured ->
                return GoalMutationOutcome.Failed("Skill Bill CLI executable override is not usable")
        }
        val canonicalRoot = try {
            projectRoot.toAbsolutePath().normalize().toRealPath()
        } catch (_: Exception) {
            return GoalMutationOutcome.Failed("Project root is not a usable path")
        }

        val result = try {
            processRunner.runCoalesced(
                ProcessSpec(
                    command = listOf(executable) + mutation.verb + listOf(key, REPO_ROOT_OPTION, canonicalRoot.toString()),
                    timeoutMs = timeoutMs,
                    stdoutLimitBytes = DEFAULT_STDOUT_LIMIT_BYTES,
                    stderrLimitBytes = DEFAULT_STDERR_LIMIT_BYTES,
                ),
            )
        } catch (_: Exception) {
            return GoalMutationOutcome.Failed(mutation.launchFailureSummary)
        }

        return when {
            result.cancelled -> GoalMutationOutcome.Failed(mutation.cancelledSummary)
            result.timedOut -> GoalMutationOutcome.Failed(mutation.timedOutSummary)
            result.exitCode == 0 -> GoalMutationOutcome.Requested
            else -> GoalMutationOutcome.Failed(mutation.declinedSummary)
        }
    }
}
