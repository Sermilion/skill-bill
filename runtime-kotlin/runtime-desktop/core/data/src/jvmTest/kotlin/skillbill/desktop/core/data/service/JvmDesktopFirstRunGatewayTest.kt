package skillbill.desktop.core.data.service

import kotlinx.coroutines.runBlocking
import skillbill.desktop.core.domain.model.FirstRunApplyResult
import skillbill.desktop.core.domain.model.FirstRunInstallStatus
import skillbill.desktop.core.domain.model.FirstRunPlanResult
import skillbill.desktop.core.domain.model.FirstRunSetupRequest
import skillbill.desktop.core.domain.model.FirstRunTelemetryLevel
import skillbill.install.model.InstallAgent
import skillbill.install.model.InstallAgentSelectionMode
import skillbill.install.model.InstallAgentTarget
import skillbill.install.model.InstallAgentTargetSource
import skillbill.install.model.InstallAppliedSkill
import skillbill.install.model.InstallApplyIssue
import skillbill.install.model.InstallApplyIssueKind
import skillbill.install.model.InstallApplyResult
import skillbill.install.model.InstallApplyStatus
import skillbill.install.model.InstallPlan
import skillbill.install.model.InstallPlanRequest
import skillbill.install.model.InstallPlanSkill
import skillbill.install.model.InstallPlanSkillKind
import skillbill.install.model.InstallSkillStagingOutcome
import skillbill.install.model.InstallSkillStagingStatus
import skillbill.install.model.InstallStagingIntent
import skillbill.install.model.InstallTelemetryApplyOutcome
import skillbill.install.model.InstallTelemetryApplyStatus
import skillbill.install.model.McpRegistrationApplyOutcome
import skillbill.install.model.McpRegistrationApplyStatus
import skillbill.install.model.McpRegistrationIntent
import skillbill.install.model.NativeAgentApplyOutcome
import skillbill.install.model.NativeAgentApplyStatus
import skillbill.install.model.NativeAgentProviderId
import skillbill.install.model.PlannedPlatformPack
import skillbill.install.model.WindowsSymlinkApplyOutcome
import skillbill.install.model.WindowsSymlinkFallbackState
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmDesktopFirstRunGatewayTest {
  @Test
  fun `plan maps desktop request into shared install plan request`() = runBlocking {
    val root = Files.createTempDirectory("skillbill-first-run-root")
    var capturedRequest: InstallPlanRequest? = null
    val gateway = JvmDesktopFirstRunGateway().apply {
      repoRootProvider = { root }
      homeProvider = { root.resolve("home") }
      planInstall = { request ->
        capturedRequest = request
        request.toPlan()
      }
    }

    val result = gateway.planSetup(
      FirstRunSetupRequest(
        selectedAgentIds = setOf("codex", "claude"),
        selectedPlatformSlugs = setOf("kotlin"),
        telemetryLevel = FirstRunTelemetryLevel.FULL,
        registerMcp = false,
      ),
    )

    assertIs<FirstRunPlanResult.Planned>(result)
    val request = checkNotNull(capturedRequest)
    assertEquals(InstallAgentSelectionMode.MANUAL, request.agentSelection.mode)
    assertEquals(setOf(InstallAgent.CLAUDE, InstallAgent.CODEX), request.agentSelection.manualAgents)
    assertEquals(setOf("kotlin"), request.platformPackSelection.selectedSlugs)
    assertEquals("full", request.telemetryLevel.id)
    assertFalse(request.mcpRegistrationChoice.register)
    assertEquals(root.resolve("skills").toAbsolutePath().normalize(), request.targetPaths.skillsRoot)
    assertEquals(root.resolve("platform-packs").toAbsolutePath().normalize(), request.targetPaths.platformPacksRoot)
    assertTrue(result.plan.platformPacks.single().selected)
    assertEquals(1, result.plan.baseSkillCount)
    assertEquals(1, result.plan.platformSkillCount)
  }

  @Test
  fun `apply maps structured backend warnings and outcomes`() = runBlocking {
    val root = Files.createTempDirectory("skillbill-first-run-apply")
    val gateway = JvmDesktopFirstRunGateway().apply {
      repoRootProvider = { root }
      homeProvider = { root.resolve("home") }
      planInstall = { request -> request.toPlan() }
      applyInstall = { plan ->
        InstallApplyResult(
          status = InstallApplyStatus.WARNING,
          skills = listOf(
            InstallAppliedSkill(
              skillName = "bill-feature-implement",
              kind = InstallPlanSkillKind.BASE,
              sourceDir = plan.request.targetPaths.skillsRoot.resolve("bill-feature-implement"),
              staging = InstallSkillStagingOutcome(
                status = InstallSkillStagingStatus.STAGED,
                sourceDir = plan.request.targetPaths.skillsRoot.resolve("bill-feature-implement"),
                stagingDir = plan.staging.root.resolve("bill-feature-implement-hash"),
              ),
            ),
          ),
          nativeAgents = listOf(
            NativeAgentApplyOutcome(
              provider = NativeAgentProviderId.CODEX,
              agent = InstallAgent.CODEX,
              status = NativeAgentApplyStatus.LINKED,
              path = root.resolve("home/.codex/agents/bill-review.md"),
              message = "Native agent linked.",
            ),
          ),
          telemetryOutcome = InstallTelemetryApplyOutcome(
            level = plan.telemetryLevel,
            status = InstallTelemetryApplyStatus.SUCCESS,
            configPath = root.resolve("home/.skill-bill/config.json"),
            message = "Telemetry level set to 'anonymous'.",
          ),
          mcpRegistrationOutcomes = listOf(
            McpRegistrationApplyOutcome(
              agent = InstallAgent.CODEX,
              status = McpRegistrationApplyStatus.FAILED,
              configPath = root.resolve("home/.codex/config.toml"),
              message = "MCP registration failed.",
              issue = InstallApplyIssue(
                kind = InstallApplyIssueKind.MCP_REGISTRATION_FAILED,
                message = "runtime-mcp missing",
                agent = InstallAgent.CODEX,
              ),
            ),
          ),
          warnings = listOf(
            InstallApplyIssue(
              kind = InstallApplyIssueKind.WINDOWS_SYMLINK_WARNING,
              message = "Windows requires Developer Mode.",
              guidance = "Enable Developer Mode or run elevated.",
            ),
          ),
          failures = emptyList(),
          windowsSymlinkOutcome = WindowsSymlinkApplyOutcome(
            preflight = plan.windowsSymlinkPreflight.copy(message = "Windows requires Developer Mode."),
            fallbackState = WindowsSymlinkFallbackState.PROCEEDING,
            guidance = "Enable Developer Mode or run elevated.",
          ),
          telemetryLevel = plan.telemetryLevel,
          mcpRegistrationIntent = plan.mcpRegistrationIntent,
        )
      }
    }
    val planned = assertIs<FirstRunPlanResult.Planned>(
      gateway.planSetup(
        FirstRunSetupRequest(
          selectedAgentIds = setOf("codex"),
          selectedPlatformSlugs = emptySet(),
          telemetryLevel = FirstRunTelemetryLevel.ANONYMOUS,
          registerMcp = true,
        ),
      ),
    )

    val applied = assertIs<FirstRunApplyResult.Applied>(gateway.applySetup(planned.plan))

    assertEquals(FirstRunInstallStatus.WARNING, applied.outcome.status)
    assertTrue(applied.outcome.details.any { detail -> detail.message == "Windows requires Developer Mode." })
    assertTrue(applied.outcome.details.any { detail -> detail.message == "Telemetry level set to 'anonymous'." })
    assertTrue(applied.outcome.details.any { detail -> detail.message == "MCP registration failed." })
    assertTrue(applied.outcome.details.any { detail -> detail.message == "Native agent linked." })
  }
}

private fun InstallPlanRequest.toPlan(): InstallPlan {
  val agents = agentSelection.manualAgents.sortedBy(InstallAgent::id).map { agent ->
    InstallAgentTarget(
      agent = agent,
      path = home.resolve(".${agent.id}/agents"),
      source = InstallAgentTargetSource.MANUAL,
    )
  }
  val selectedPlatforms = platformPackSelection.selectedSlugs.sorted()
  val platformPackRoot = targetPaths.platformPacksRoot.resolve("kotlin")
  return InstallPlan(
    request = this,
    agents = agents,
    discoveredPlatformPacks = listOf(
      PlannedPlatformPack(
        slug = "kotlin",
        packRoot = platformPackRoot,
        selected = "kotlin" in selectedPlatforms,
      ),
    ),
    selectedPlatformSlugs = selectedPlatforms,
    skills = listOf(
      InstallPlanSkill(
        name = "bill-feature-implement",
        sourceDir = targetPaths.skillsRoot.resolve("bill-feature-implement"),
        kind = InstallPlanSkillKind.BASE,
      ),
      InstallPlanSkill(
        name = "bill-kotlin-code-review",
        sourceDir = platformPackRoot.resolve("bill-kotlin-code-review"),
        kind = InstallPlanSkillKind.PLATFORM_PACK,
        platformSlug = "kotlin",
      ),
    ),
    staging = InstallStagingIntent(
      root = home.resolve(".skill-bill/installed-skills"),
      skillPaths = emptyList(),
    ),
    telemetryLevel = telemetryLevel,
    mcpRegistrationIntent = McpRegistrationIntent(
      register = mcpRegistrationChoice.register,
      runtimeMcpBin = mcpRegistrationChoice.runtimeMcpBin,
      agents = agents.map(InstallAgentTarget::agent),
    ),
    runtimeDistributionInputs = runtimeDistributionInputs,
    installationTargetPaths = targetPaths.copy(agentTargets = agents),
    windowsSymlinkPreflight = windowsSymlinkPreflight,
  )
}
