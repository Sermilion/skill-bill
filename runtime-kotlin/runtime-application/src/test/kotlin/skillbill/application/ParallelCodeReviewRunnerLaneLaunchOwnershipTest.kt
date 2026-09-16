package skillbill.application

import skillbill.application.reviewevidence.model.ParallelReviewScope
import skillbill.install.model.InstallAgent
import skillbill.ports.agentrun.model.UnsupportedAgentRunLaunch
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.review.GovernedReviewEvidenceEndpointBinder
import skillbill.ports.review.GovernedReviewEvidenceEndpointHandle
import skillbill.ports.review.NativeReviewOperationProtocol
import skillbill.ports.review.ReviewLaunchAgentStagingPort
import skillbill.ports.review.stubGovernedReviewEvidenceEndpointBinder
import skillbill.review.context.model.CodeReviewExecutionMode
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ParallelCodeReviewRunnerLaneLaunchOwnershipTest {
  @Test
  fun `staging failure after bind closes the endpoint once and launches no worker`() {
    val endpointRoot = Files.createTempDirectory("staging-failure-endpoint")
    val closeCount = AtomicInteger(0)
    val launcher = RecordingSubtaskLauncher()
    val runner = createRunner(
      launcher,
      RunnerFixtureConfig(
        evidenceEndpointRoot = endpointRoot,
        catalogGateway = stubCatalogGateway(listOf(platformManifest("kotlin", listOf("*.kt")))),
        diffResolver = RecordingDiffResolver(default = diffFor("src/FooTest.kt")),
        rubricResolver = delegatedCursorRubricResolver(),
        reviewLaunchAgentStaging = ReviewLaunchAgentStagingPort { throw IllegalStateException("staging failed") },
        evidenceEndpointBinder = countingEndpointBinder(endpointRoot, closeCount),
      ),
    )

    val result = runner.run(
      baseRequest(agent1Id = "cursor", scope = ParallelReviewScope.STAGED)
        .copy(codeReviewMode = CodeReviewExecutionMode.DELEGATED),
    )

    assertTrue(launcher.requests.isEmpty())
    assertEquals(1, closeCount.get())
    assertEquals(false, result.lane1.success)
  }

  @Test
  fun `endpoint bind interruption propagates instead of becoming unbound`() {
    val launcher = RecordingSubtaskLauncher()
    val runner = createRunner(
      launcher,
      RunnerFixtureConfig(
        catalogGateway = stubCatalogGateway(listOf(platformManifest("kotlin", listOf("*.kt")))),
        diffResolver = RecordingDiffResolver(default = diffFor("src/FooTest.kt")),
        rubricResolver = delegatedCursorRubricResolver(),
        evidenceEndpointBinder = object : GovernedReviewEvidenceEndpointBinder {
          override fun bind(
            lane: String,
            protocol: NativeReviewOperationProtocol,
            onEvidenceRead: (() -> Unit)?,
          ): GovernedReviewEvidenceEndpointHandle = throw InterruptedException("bind interrupted")
        },
      ),
    )

    assertFailsWith<InterruptedException> {
      runner.run(
        baseRequest(agent1Id = "cursor", scope = ParallelReviewScope.STAGED)
          .copy(codeReviewMode = CodeReviewExecutionMode.DELEGATED),
      )
    }
    assertTrue(launcher.requests.isEmpty())
  }

  @Test
  fun `lane launch ownership matrix preserves primary failure and exactly once close`() {
    val cases = listOf(
      LaunchOwnershipCase(
        name = "successful launch",
        staging = ReviewLaunchAgentStagingPort { },
        launcher = RecordingSubtaskLauncher(),
        expectLaunch = true,
        expectSuccess = true,
      ),
      LaunchOwnershipCase(
        name = "unsupported launch",
        staging = ReviewLaunchAgentStagingPort { },
        launcher = GoalRunnerSubtaskLauncher {
          UnsupportedAgentRunLaunch(
            agent = InstallAgent.fromNormalizedId("cursor", label = "agentId"),
            reason = "unsupported",
          )
        },
        expectLaunch = true,
        expectSuccess = false,
      ),
      LaunchOwnershipCase(
        name = "throwing launch",
        staging = ReviewLaunchAgentStagingPort { },
        launcher = GoalRunnerSubtaskLauncher { throw IllegalStateException("launch failed") },
        expectLaunch = false,
        expectSuccess = false,
      ),
      LaunchOwnershipCase(
        name = "cancellation",
        staging = ReviewLaunchAgentStagingPort { },
        launcher = GoalRunnerSubtaskLauncher { throw CancellationException("cancelled") },
        expectLaunch = false,
        expectSuccess = false,
        expectThrown = CancellationException::class,
      ),
      LaunchOwnershipCase(
        name = "interrupted launch",
        staging = ReviewLaunchAgentStagingPort { },
        launcher = GoalRunnerSubtaskLauncher { throw InterruptedException("interrupted") },
        expectLaunch = false,
        expectSuccess = false,
        expectThrown = InterruptedException::class,
      ),
      LaunchOwnershipCase(
        name = "staging failure",
        staging = ReviewLaunchAgentStagingPort { throw IllegalStateException("staging failed") },
        launcher = RecordingSubtaskLauncher(),
        expectLaunch = false,
        expectSuccess = false,
      ),
    )

    cases.forEach { case ->
      val endpointRoot = Files.createTempDirectory("lane-ownership-${case.name}")
      val closeCount = AtomicInteger(0)
      val runner = createRunner(
        case.launcher,
        RunnerFixtureConfig(
          evidenceEndpointRoot = endpointRoot,
          catalogGateway = stubCatalogGateway(listOf(platformManifest("kotlin", listOf("*.kt")))),
          diffResolver = RecordingDiffResolver(default = diffFor("src/FooTest.kt")),
          rubricResolver = delegatedCursorRubricResolver(),
          reviewLaunchAgentStaging = case.staging,
          evidenceEndpointBinder = countingEndpointBinder(endpointRoot, closeCount),
        ),
      )
      val run = {
        runner.run(
          baseRequest(agent1Id = "cursor", scope = ParallelReviewScope.STAGED)
            .copy(codeReviewMode = CodeReviewExecutionMode.DELEGATED),
        )
      }
      if (case.expectThrown != null) {
        assertFailsWith(case.expectThrown) { run() }
      } else {
        val result = run()
        assertEquals(case.expectSuccess, result.lane1.success, case.name)
      }
      if (case.launcher is RecordingSubtaskLauncher) {
        val launchCount = case.launcher.requests.size
        if (case.expectLaunch) {
          assertEquals(1, launchCount, case.name)
        } else {
          assertEquals(0, launchCount, case.name)
        }
      }
      assertEquals(1, closeCount.get(), case.name)
    }
  }

  @Test
  fun `interrupted launch preserves its primary failure when cleanup also fails`() {
    val endpointRoot = Files.createTempDirectory("lane-cleanup-failure")
    val closeCount = AtomicInteger(0)
    val primary = InterruptedException("launch interrupted")
    val cleanup = IllegalStateException("cleanup failed")
    val runner = createRunner(
      GoalRunnerSubtaskLauncher { throw primary },
      RunnerFixtureConfig(
        evidenceEndpointRoot = endpointRoot,
        catalogGateway = stubCatalogGateway(listOf(platformManifest("kotlin", listOf("*.kt")))),
        diffResolver = RecordingDiffResolver(default = diffFor("src/FooTest.kt")),
        rubricResolver = delegatedCursorRubricResolver(),
        evidenceEndpointBinder = countingEndpointBinder(
          endpointRoot,
          closeCount,
          closeFailure = cleanup,
        ),
      ),
    )

    val error = assertFailsWith<InterruptedException> {
      runner.run(
        baseRequest(agent1Id = "cursor", scope = ParallelReviewScope.STAGED)
          .copy(codeReviewMode = CodeReviewExecutionMode.DELEGATED),
      )
    }

    assertSame(primary, error)
    assertEquals(listOf(cleanup), error.suppressed.toList())
    assertEquals(1, closeCount.get())
  }
}

private data class LaunchOwnershipCase(
  val name: String,
  val staging: ReviewLaunchAgentStagingPort,
  val launcher: GoalRunnerSubtaskLauncher,
  val expectLaunch: Boolean,
  val expectSuccess: Boolean,
  val expectThrown: kotlin.reflect.KClass<out Throwable>? = null,
)

private fun countingEndpointBinder(
  root: java.nio.file.Path,
  closeCount: AtomicInteger,
  closeFailure: Throwable? = null,
): GovernedReviewEvidenceEndpointBinder =
  object : GovernedReviewEvidenceEndpointBinder {
    override fun bind(
      lane: String,
      protocol: NativeReviewOperationProtocol,
      onEvidenceRead: (() -> Unit)?,
    ): GovernedReviewEvidenceEndpointHandle {
      val delegate = stubGovernedReviewEvidenceEndpointBinder(root).bind(lane, protocol, onEvidenceRead)
      return object : GovernedReviewEvidenceEndpointHandle {
        override val descriptor = delegate.descriptor

        override fun close() {
          closeCount.incrementAndGet()
          delegate.close()
          closeFailure?.let { throw it }
        }
      }
    }
  }

private class RecordingSubtaskLauncher : GoalRunnerSubtaskLauncher {
  val requests = mutableListOf<skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest>()

  override fun launch(request: skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest) =
    skillbill.ports.agentrun.model.AgentRunLaunchFacts(
      agent = skillbill.install.model.InstallAgent.fromNormalizedId(request.invokedAgentId, label = "agentId"),
      exitStatus = 0,
      stdout = "NO_FINDINGS",
      stderr = "",
      timedOut = false,
      interrupted = false,
      spawnFailed = false,
      liveness = null,
      processStarted = true,
      mcpStartupObserved = false,
    ).also { requests += request }
}

private fun delegatedCursorRubricResolver(): skillbill.ports.review.ReviewRubricResolver =
  skillbill.ports.review.ReviewRubricResolver {
    skillbill.ports.review.model.ResolvedReviewRubric(
      "bill-kotlin-code-review",
      "parent routing rubric",
      specialists = listOf(
        skillbill.ports.review.model.ResolvedReviewRubric(
          "bill-kotlin-code-review-architecture",
          "architecture specialist rubric",
          area = "architecture",
        ),
      ),
    )
  }
