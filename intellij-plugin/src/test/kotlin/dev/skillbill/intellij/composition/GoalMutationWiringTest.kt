package dev.skillbill.intellij.composition

import dev.skillbill.intellij.domain.SkillBillStatusOutcome
import dev.skillbill.intellij.fakes.FakePreferenceCache
import dev.skillbill.intellij.fakes.FakeStatusRepository
import dev.skillbill.intellij.fakes.ScriptedProcessFactory
import dev.skillbill.intellij.infrastructure.cli.ProcessRunner
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalMutationWiringTest {
    @Test
    fun `the composed pause and stop repositories each issue their own verb`() {
        val projectRoot = Files.createTempDirectory("mutation-wiring")
        val executable = Files.createTempFile("skill-bill", "")
        executable.toFile().setExecutable(true)
        val pauseFactory = ScriptedProcessFactory(exitCode = 0)
        val stopFactory = ScriptedProcessFactory(exitCode = 0)

        val composed = SkillBillStatusCompositionRoot.createForTest(
            projectRoot = projectRoot,
            preferences = FakePreferenceCache(cliOverride = executable.toString()),
            statusRepository = FakeStatusRepository { SkillBillStatusOutcome.Idle(Instant.now(), "idle") },
            pauseProcessRunner = ProcessRunner(processFactory = pauseFactory),
            stopProcessRunner = ProcessRunner(processFactory = stopFactory),
        )

        try {
            runBlocking {
                withContext(Dispatchers.Default) {
                    composed.goalPauseRepository.requestMutation(projectRoot, "SKILL-238")
                    composed.goalStopRepository.requestMutation(projectRoot, "SKILL-238")
                }
            }
            assertEquals(
                listOf(executable.toString(), "goal", "pause", "SKILL-238"),
                pauseFactory.commands.single().take(4),
            )
            assertEquals(
                listOf(executable.toString(), "goal", "stop", "SKILL-238"),
                stopFactory.commands.single().take(4),
            )
        } finally {
            composed.dispose()
        }
    }
}
