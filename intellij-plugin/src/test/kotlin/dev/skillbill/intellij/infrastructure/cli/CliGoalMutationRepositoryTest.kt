package dev.skillbill.intellij.infrastructure.cli

import dev.skillbill.intellij.application.GoalMutationOutcome
import dev.skillbill.intellij.fakes.FakePreferenceCache
import dev.skillbill.intellij.fakes.ScriptedProcessFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exact-argv and failure-summary coverage for both mutating verbs. Summaries must never
 * carry stdout, stderr, exception text, or a filesystem path.
 */
class CliGoalMutationRepositoryTest {
    private val secretStdout = "SECRET_STDOUT_MARKER"
    private val prefs = FakePreferenceCache()

    @Test
    fun `pause and stop send the exact argv with a canonical repo root`() {
        val root = Files.createTempDirectory("goal-mutation")
        val canonical = root.toAbsolutePath().normalize().toRealPath().toString()

        val pauseFactory = ScriptedProcessFactory(exitCode = 0)
        val pause = repository(GoalMutation.PAUSE, ProcessRunner(processFactory = pauseFactory))
        val pauseOutcome = runBlocking { pause.requestMutation(root.resolve("..").resolve(root.fileName), "SKILL-168") }
        assertEquals(GoalMutationOutcome.Requested, pauseOutcome)
        assertEquals(
            listOf("/usr/bin/skill-bill", "goal", "pause", "SKILL-168", "--repo-root", canonical),
            pauseFactory.commands.single(),
        )

        val stopFactory = ScriptedProcessFactory(exitCode = 0)
        val stop = repository(GoalMutation.STOP, ProcessRunner(processFactory = stopFactory))
        val stopOutcome = runBlocking { stop.requestMutation(root, "SKILL-168") }
        assertEquals(GoalMutationOutcome.Requested, stopOutcome)
        assertEquals(
            listOf("/usr/bin/skill-bill", "goal", "stop", "SKILL-168", "--repo-root", canonical),
            stopFactory.commands.single(),
        )
    }

    @Test
    fun `a blank or whitespace issue key never starts a process`() {
        for (key in listOf("", "   ", "\t")) {
            val pauseFactory = ScriptedProcessFactory()
            val pause = repository(GoalMutation.PAUSE, ProcessRunner(processFactory = pauseFactory))
            val stopFactory = ScriptedProcessFactory()
            val stop = repository(GoalMutation.STOP, ProcessRunner(processFactory = stopFactory))
            val root = Files.createTempDirectory("blank-key")
            runBlocking {
                assertTrue(pause.requestMutation(root, key) is GoalMutationOutcome.Failed)
                assertTrue(stop.requestMutation(root, key) is GoalMutationOutcome.Failed)
            }
            assertTrue("blank key must not spawn a process", pauseFactory.commands.isEmpty())
            assertTrue("blank key must not spawn a process", stopFactory.commands.isEmpty())
        }
    }

    @Test
    fun `every failure summary is bounded and leaks no output or path`() {
        val root = Files.createTempDirectory("leak-check")
        val canonical = root.toAbsolutePath().normalize().toRealPath().toString()
        val summaries = mutableListOf<String>()

        summaries += failureSummaries(root) { ProcessRunner(processFactory = ScriptedProcessFactory(exitCode = 3, stdout = secretStdout)) }
        summaries += failureSummaries(root, resolution = CliExecutableResolution.Missing) {
            ProcessRunner(processFactory = ScriptedProcessFactory())
        }
        summaries += failureSummaries(root, resolution = CliExecutableResolution.Misconfigured) {
            ProcessRunner(processFactory = ScriptedProcessFactory())
        }
        summaries += failureSummaries(root) {
            ProcessRunner(
                processFactory = { _, _ -> throw IllegalStateException("BOOM_$secretStdout at $canonical") },
            )
        }
        summaries += failureSummaries(root) {
            ProcessRunner(processFactory = ScriptedProcessFactory()).apply { cancelAll() }
        }
        summaries += failureSummaries(root, timeoutMs = 50) {
            ProcessRunner(processFactory = ScriptedProcessFactory(hold = true))
        }
        summaries += failureSummaries(Path.of("/definitely/not/a/real/root/for/skill-bill")) {
            ProcessRunner(processFactory = ScriptedProcessFactory())
        }

        assertTrue("expected every failure path to be covered", summaries.size >= 14)
        for (summary in summaries) {
            assertTrue("summary must be non-empty", summary.isNotBlank())
            assertTrue("summary must stay bounded: $summary", summary.length <= 120)
            assertTrue("summary leaked stdout: $summary", !summary.contains(secretStdout))
            assertTrue("summary leaked an exception: $summary", !summary.contains("BOOM"))
            assertTrue("summary leaked a path: $summary", !summary.contains(canonical))
            assertTrue("summary leaked a path: $summary", !summary.contains("/"))
        }
    }

    private fun repository(
        mutation: GoalMutation,
        runner: ProcessRunner,
        resolution: CliExecutableResolution =
            CliExecutableResolution.Found("/usr/bin/skill-bill", CliExecutableSource.SEARCH_PATH),
        timeoutMs: Long = 2_000,
    ) = CliGoalMutationRepository(mutation, prefs, runner, { resolution }, timeoutMs)

    private fun failureSummaries(
        root: Path,
        resolution: CliExecutableResolution = CliExecutableResolution.Found("/usr/bin/skill-bill", CliExecutableSource.SEARCH_PATH),
        timeoutMs: Long = 2_000,
        runner: () -> ProcessRunner,
    ): List<String> = runBlocking {
        val pause = repository(GoalMutation.PAUSE, runner(), resolution, timeoutMs)
            .requestMutation(root, "SKILL-168")
        val stop = repository(GoalMutation.STOP, runner(), resolution, timeoutMs)
            .requestMutation(root, "SKILL-168")
        listOfNotNull(
            (pause as? GoalMutationOutcome.Failed)?.summary,
            (stop as? GoalMutationOutcome.Failed)?.summary,
        )
    }
}
