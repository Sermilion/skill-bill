package skillbill.engine.goalrunner.experiment

import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.engine.RecordingWorkflowGitOperations
import skillbill.engine.goalrunner.model.GoalRunnerRunRequest
import skillbill.error.shellcontent.ExperimentDirtySourceRefusalError
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.goalrunner.model.GoalPullRequestStatus
import skillbill.goalrunner.model.GoalRunnerRunReport
import skillbill.goalrunner.model.GoalRunnerStopReason
import skillbill.goalrunner.model.GoalRunnerStopReport
import skillbill.ports.experiment.isolation.ExperimentArmIsolationContext
import skillbill.ports.experiment.isolation.ExperimentIsolationCapabilityPort
import skillbill.ports.experiment.isolation.ExperimentIsolationObservation
import skillbill.ports.experiment.measurement.ExperimentArmMeasurement
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.measurement.ExperimentMeasuredValue
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPayload
import skillbill.ports.experiment.pair.ExperimentPairPersistedState
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.experiment.publication.ExperimentPublicationResult
import skillbill.ports.experiment.selection.ExperimentLaunchSelection
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperimentPairCoordinatorTest {
  private fun fixtureCoordinator(
    owner: InMemoryOwner,
    requests: MutableList<GoalRunnerRunRequest>,
    isolationContexts: MutableList<ExperimentArmIsolationContext>,
    deliveryCalls: MutableList<Triple<String, String, Boolean>>,
  ): ExperimentPairCoordinator =
    ExperimentPairCoordinator(
      goalRunner =
        ExperimentGoalRunnerPort { request ->
          requests += request
          GoalRunnerRunReport.Completed(
            issueKey = request.issueKey,
            attemptedSubtasks = listOf(1),
            pullRequestUrl = null,
            pullRequestStatus = GoalPullRequestStatus.EXISTING,
            subtasksCompleted = 1,
            subtasksPending = 0,
            subtasksBlocked = 0,
            parentWorkflowId = "${request.experimentPairId}:${request.experimentArmId?.wireValue}",
          )
        },
      selectionPort = fixtureSelectionPort(),
      pairOwner = owner,
      gitOperations = fixtureGitOperations(),
      isolationCapability = fixtureIsolationCapability(isolationContexts),
      parentDelivery =
        ExperimentParentDeliveryPort { pair, workflow, _, completed, _ ->
          deliveryCalls += Triple(pair, workflow, completed)
          ExperimentPublicationResult(published = completed)
        },
      measurementPort =
        ExperimentArmMeasurementPort { _, _, _ ->
          ExperimentArmMeasurement(setupCost = measured(1.0), usage = measured(2.0), cost = measured(3.0))
        },
      random = Random(0),
    )

  private fun fixtureSelectionPort() =
    object : ExperimentSelectionPort {
      override fun resolveForLaunch(
        repoRoot: Path,
        parameter: String?,
        mode: ExperimentExecutionMode,
        savedSelection: List<String>?,
      ) = ExperimentLaunchSelection(
        normalizedNames = listOf("fixture-goal"),
        descriptors = listOf("fixture-goal"),
        availabilitySummary = "explicit",
        treatmentCapabilities = setOf("fixture-treatment"),
      )
    }

  private fun fixtureGitOperations() =
    RecordingWorkflowGitOperations().also {
      it.headCommitShaValue = "a".repeat(40)
      it.repositoryFingerprintValue = "repository"
      it.worktreeStatusValue = ""
    }

  private fun fixtureIsolationCapability(isolationContexts: MutableList<ExperimentArmIsolationContext>) =
    object : ExperimentIsolationCapabilityPort {
      override fun assertLaunchSupported(context: ExperimentArmIsolationContext) {
        isolationContexts += context
        assertTrue(context.statePaths.runtimeDatabase != null)
        assertTrue(context.statePaths.learningStore != null)
        assertTrue(context.statePaths.graphIndex != null)
        assertTrue(context.statePaths.buildOutput != null)
        assertTrue(context.statePaths.writableCache != null)
        assertTrue(context.statePaths.worktreeEditJournal != null)
      }
    }

  private fun resumeOwner(sourceSha: String) =
    InMemoryOwner().also {
      it.lastState =
        ExperimentPairPersistedState(
          pairId = "pair-resume",
          executionMode = ExperimentExecutionMode.GOAL_PAIR,
          selectedNames = listOf("fixture-goal"),
          armOrder = listOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
          randomSeed = "seed",
          pairPayload =
            ExperimentPairPayload(
              mapOf(
                ExperimentPairPayloadKeys.PAIR_ID to "pair-resume",
                ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
                ExperimentPairPayloadKeys.ARM_ORDER to listOf("control", "treatment"),
                ExperimentPairPayloadKeys.RANDOM_SEED to "seed",
                ExperimentPairPayloadKeys.PAIR_STATUS to "running",
                ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
                ExperimentPairPayloadKeys.DELIVERY_STATUS to "deferred",
                ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY to
                  mapOf(
                    ExperimentPairPayloadKeys.REPOSITORY_IDENTITY to "repository",
                    ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA to sourceSha,
                    ExperimentPairPayloadKeys.SOURCE_TREE_SHA to sourceSha,
                    ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH to specBundleHash("frozen spec"),
                  ),
                ExperimentPairPayloadKeys.ARM_OUTCOMES to
                  listOf(
                    mapOf(
                      ExperimentPairPayloadKeys.ARM_ID to "control",
                      ExperimentPairPayloadKeys.WORKFLOW_ID to "control-workflow",
                      ExperimentPairPayloadKeys.TERMINAL_STATUS to "completed",
                    ),
                  ),
              ),
            ),
        )
    }

  private fun resumeCoordinator(
    owner: InMemoryOwner,
    sourceSha: String,
    launchedArms: MutableList<ExperimentArmId>,
    measuredArms: MutableList<String>,
  ) = ExperimentPairCoordinator(
    goalRunner =
      ExperimentGoalRunnerPort { request ->
        request.experimentArmId?.let { launchedArms += it }
        GoalRunnerRunReport.Completed(
          issueKey = request.issueKey,
          attemptedSubtasks = emptyList(),
          pullRequestUrl = null,
          pullRequestStatus = GoalPullRequestStatus.DEFERRED,
          subtasksCompleted = 0,
          subtasksPending = 0,
          subtasksBlocked = 0,
          parentWorkflowId = "treatment-workflow",
        )
      },
    selectionPort = resumeSelectionPort(),
    pairOwner = owner,
    gitOperations =
      RecordingWorkflowGitOperations().also {
        it.headCommitShaValue = sourceSha
        it.repositoryFingerprintValue = "repository"
        it.worktreeStatusValue = ""
      },
    isolationCapability =
      object : ExperimentIsolationCapabilityPort {
        override fun assertLaunchSupported(context: ExperimentArmIsolationContext) = Unit
      },
    measurementPort =
      ExperimentArmMeasurementPort { _, arm, _ ->
        measuredArms += arm
        ExperimentArmMeasurement(setupCost = measured(1.0), usage = measured(1.0), cost = measured(1.0))
      },
    parentDelivery =
      ExperimentParentDeliveryPort { _, _, _, _, _ ->
        ExperimentPublicationResult(published = false)
      },
  )

  private fun resumeSelectionPort() =
    object : ExperimentSelectionPort {
      override fun resolveForLaunch(
        repoRoot: Path,
        parameter: String?,
        mode: ExperimentExecutionMode,
        savedSelection: List<String>?,
      ) = ExperimentLaunchSelection(
        normalizedNames = savedSelection ?: listOf("fixture-goal"),
        descriptors = listOf("fixture-goal"),
        availabilitySummary = "saved",
      )
    }

  @Test
  fun `pair runs both arms in isolated worktree roots and persists terminal outcomes`() {
    val repository = Files.createTempDirectory("experiment-pair-source")
    val spec = repository.resolve(".feature-specs/SKILL-366/spec.md")
    Files.createDirectories(spec.parent)
    Files.writeString(spec, "frozen spec")
    val owner = InMemoryOwner()
    val requests = mutableListOf<GoalRunnerRunRequest>()
    val isolationContexts = mutableListOf<ExperimentArmIsolationContext>()
    val deliveryCalls = mutableListOf<Triple<String, String, Boolean>>()
    val coordinator = fixtureCoordinator(owner, requests, isolationContexts, deliveryCalls)

    coordinator.run(
      GoalRunnerRunRequest(
        issueKey = "SKILL-366",
        repoRoot = repository,
        invokedAgentId = "fixture-agent",
        experimentsParameter = "fixture-goal",
      ),
    )

    assertEquals(2, requests.size)
    assertEquals(
      setOf(ExperimentArmId.CONTROL, ExperimentArmId.TREATMENT),
      isolationContexts.map { it.armId }.toSet(),
    )
    assertEquals(2, isolationContexts.map { it.statePaths.runtimeDatabase }.distinct().size)
    assertEquals(2, isolationContexts.map { it.checkpointNamespacePrefix }.distinct().size)
    assertEquals(2, requests.map { it.repoRoot }.distinct().size)
    assertEquals(
      setOf("fixture-treatment"),
      requests.single { it.experimentArmId == ExperimentArmId.CONTROL }.experimentTreatmentCapabilitiesDenied,
    )
    assertEquals(
      emptySet(),
      requests.single { it.experimentArmId == ExperimentArmId.TREATMENT }.experimentTreatmentCapabilitiesDenied,
    )
    val outcomes = owner.lastState!!.pairPayload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as List<*>
    assertEquals(2, outcomes.size)
    assertTrue(outcomes.all { (it as Map<*, *>)[ExperimentPairPayloadKeys.TERMINAL_STATUS] == "completed" })
    assertEquals(1, deliveryCalls.size)
    assertTrue(deliveryCalls.single().third)

    coordinator.run(
      GoalRunnerRunRequest(
        issueKey = "SKILL-366",
        repoRoot = repository,
        invokedAgentId = "fixture-agent",
        experimentPairId = requireNotNull(requests.first().experimentPairId),
      ),
    )
    assertEquals(2, requests.size)
    assertEquals(
      requests.first().experimentPairId,
      owner.lastReport?.get(ExperimentReportPayloadKeys.PAIR_ID),
    )
  }

  @Test
  fun `resume reconciles a completed arm and launches only the unfinished arm`() {
    val repository = Files.createTempDirectory("experiment-pair-resume")
    val spec = repository.resolve(".feature-specs/SKILL-366/spec.md")
    Files.createDirectories(spec.parent)
    Files.writeString(spec, "frozen spec")
    val sourceSha = "a".repeat(40)
    val owner = resumeOwner(sourceSha)
    val launchedArms = mutableListOf<ExperimentArmId>()
    val measuredArms = mutableListOf<String>()
    val coordinator = resumeCoordinator(owner, sourceSha, launchedArms, measuredArms)

    coordinator.run(
      GoalRunnerRunRequest(
        issueKey = "SKILL-366",
        repoRoot = repository,
        invokedAgentId = "fixture-agent",
        experimentPairId = "pair-resume",
      ),
    )

    assertEquals(listOf(ExperimentArmId.TREATMENT), launchedArms)
    assertEquals(listOf("control", "treatment"), measuredArms)
  }

  @Test
  fun `failed arm is retained as failed while a resumable stop pauses the pair`() {
    val failed = stopped(GoalRunnerStopReason.FAILED)
    val paused = stopped(GoalRunnerStopReason.PAUSED)

    assertEquals("failed", failed.armTerminalStatus())
    assertFalse(failed.shouldPausePair())
    assertEquals("paused", paused.armTerminalStatus())
    assertTrue(paused.shouldPausePair())
  }

  @Test
  fun `dirty source is refused before either arm starts`() {
    val repository = Files.createTempDirectory("experiment-dirty-source")
    val spec = repository.resolve(".feature-specs/SKILL-366/spec.md")
    Files.createDirectories(spec.parent)
    Files.writeString(spec, "frozen spec")
    val requests = mutableListOf<GoalRunnerRunRequest>()
    val git =
      RecordingWorkflowGitOperations().also {
        it.headCommitShaValue = "a".repeat(40)
        it.repositoryFingerprintValue = "repository"
        it.worktreeStatusValue = " M user.txt"
      }
    val coordinator =
      ExperimentPairCoordinator(
        goalRunner =
          ExperimentGoalRunnerPort { request ->
            requests += request
            error("dirty source must stop before launching an arm")
          },
        selectionPort =
          object : ExperimentSelectionPort {
            override fun resolveForLaunch(
              repoRoot: Path,
              parameter: String?,
              mode: ExperimentExecutionMode,
              savedSelection: List<String>?,
            ) = ExperimentLaunchSelection(
              normalizedNames = listOf("fixture-goal"),
              descriptors = listOf("fixture-goal"),
              availabilitySummary = "explicit",
            )
          },
        pairOwner = InMemoryOwner(),
        gitOperations = git,
        isolationCapability =
          object : ExperimentIsolationCapabilityPort {
            override fun assertLaunchSupported(context: ExperimentArmIsolationContext) = Unit
          },
        measurementPort =
          ExperimentArmMeasurementPort { _, _, _ ->
            ExperimentArmMeasurement(
              setupCost = measured(1.0),
              usage = measured(1.0),
              cost = measured(1.0),
            )
          },
        parentDelivery =
          ExperimentParentDeliveryPort { _, _, _, _, _ ->
            ExperimentPublicationResult(published = false)
          },
      )

    assertFailsWith<ExperimentDirtySourceRefusalError> {
      coordinator.run(
        GoalRunnerRunRequest(
          issueKey = "SKILL-366",
          repoRoot = repository,
          invokedAgentId = "fixture-agent",
          experimentsParameter = "fixture-goal",
        ),
      )
    }
    assertEquals(emptyList(), requests)
  }

  @Test
  fun `observed isolation breach invalidates the arm outcome`() {
    val repository = Files.createTempDirectory("experiment-policy-breach")
    val spec = repository.resolve(".feature-specs/SKILL-366/spec.md")
    Files.createDirectories(spec.parent)
    Files.writeString(spec, "frozen spec")
    val owner = InMemoryOwner()
    val requests = mutableListOf<GoalRunnerRunRequest>()
    val isolation =
      object : ExperimentIsolationCapabilityPort {
        override fun assertLaunchSupported(context: ExperimentArmIsolationContext) = Unit

        override fun observe(context: ExperimentArmIsolationContext) =
          ExperimentIsolationObservation(listOf("shared learning result was written"))
      }
    val git =
      RecordingWorkflowGitOperations().also {
        it.headCommitShaValue = "a".repeat(40)
        it.repositoryFingerprintValue = "repository"
        it.worktreeStatusValue = ""
      }
    val coordinator =
      ExperimentPairCoordinator(
        goalRunner =
          ExperimentGoalRunnerPort { request ->
            requests += request
            GoalRunnerRunReport.Completed(
              issueKey = request.issueKey,
              attemptedSubtasks = listOf(1),
              pullRequestUrl = null,
              pullRequestStatus = GoalPullRequestStatus.DEFERRED,
              subtasksCompleted = 1,
              subtasksPending = 0,
              subtasksBlocked = 0,
              parentWorkflowId = "workflow-${request.experimentArmId?.wireValue}",
            )
          },
        selectionPort =
          object : ExperimentSelectionPort {
            override fun resolveForLaunch(
              repoRoot: Path,
              parameter: String?,
              mode: ExperimentExecutionMode,
              savedSelection: List<String>?,
            ) = ExperimentLaunchSelection(
              normalizedNames = listOf("fixture-goal"),
              descriptors = listOf("fixture-goal"),
              availabilitySummary = "explicit",
            )
          },
        pairOwner = owner,
        gitOperations = git,
        isolationCapability = isolation,
        measurementPort =
          ExperimentArmMeasurementPort { _, _, _ ->
            ExperimentArmMeasurement(
              setupCost = measured(1.0),
              usage = measured(1.0),
              cost = measured(1.0),
            )
          },
        parentDelivery =
          ExperimentParentDeliveryPort { _, _, _, _, _ ->
            ExperimentPublicationResult(published = false)
          },
      )

    coordinator.run(
      GoalRunnerRunRequest(
        issueKey = "SKILL-366",
        repoRoot = repository,
        invokedAgentId = "fixture-agent",
        experimentsParameter = "fixture-goal",
      ),
    )

    assertIsolationBreachOutcomes(owner)
    assertTrue(requests.isNotEmpty())
  }

  private fun assertIsolationBreachOutcomes(owner: InMemoryOwner) {
    val outcomes = owner.lastState!!.pairPayload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as List<*>
    assertTrue(outcomes.isNotEmpty())
    assertTrue(
      outcomes.all { outcome ->
        (outcome as Map<*, *>)[ExperimentPairPayloadKeys.FAILURE_REASON]
          .toString().contains("isolation policy breach")
      },
    )
  }

  private fun stopped(reason: GoalRunnerStopReason): GoalRunnerRunReport.Stopped =
    GoalRunnerRunReport.Stopped(
      issueKey = "SKILL-366",
      attemptedSubtasks = listOf(1),
      stop =
        GoalRunnerStopReport(
          issueKey = "SKILL-366",
          subtaskId = 1,
          reason = reason,
          blockedReason = "test outcome",
          workflowId = "workflow-1",
          lastResumableStep = "review",
        ),
    )

  private class InMemoryOwner : ExperimentPairOwnerPort {
    var lastState: ExperimentPairPersistedState? = null
    var lastReport: ExperimentPairPayload? = null

    override fun load(pairId: String): ExperimentPairPersistedState? = lastState?.takeIf { it.pairId == pairId }

    override fun save(state: ExperimentPairPersistedState) {
      lastState = state
    }

    override fun saveReport(
      pairId: String,
      reportPayload: ExperimentPairPayload,
    ) {
      lastReport = reportPayload
    }

    override fun importObservation(payload: ExperimentPairPayload): Boolean = true
  }

  private fun measured(quantity: Double) =
    ExperimentMeasuredValue(
      quantity = quantity,
      availability = TelemetryMeasurementAvailability.MEASURED.wireValue,
    )

  private fun specBundleHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("spec.md".encodeToByteArray())
    digest.update(0)
    digest.update(value.encodeToByteArray())
    digest.update(0)
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
  }
}
