package skillbill.engine.goalrunner.experiment

import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.engine.RecordingWorkflowGitOperations
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.error.shellcontent.ExperimentNavigationRevisionError
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRequest
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionResult
import skillbill.ports.experiment.navigation.ExperimentNavigationSessionRunnerPort
import skillbill.ports.experiment.navigation.ExperimentNavigationTerminalOutcome
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPersistedState
import skillbill.ports.experiment.selection.ExperimentLaunchSelection
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ExperimentNavigationPairCoordinatorTest {
  @Test
  fun `both arms receive the resolved revision and exact criteria`() {
    val requests = mutableListOf<ExperimentNavigationSessionRequest>()
    val gitOperations =
      RecordingWorkflowGitOperations().also {
        it.onResolveCommit = { WorkflowGitOperationResult.Ok(value = "resolved-commit") }
        it.repositoryFingerprintValue = "repo"
        it.worktreeStatusValue = ""
      }
    val coordinator =
      ExperimentNavigationPairCoordinator(
        selectionPort =
          object : ExperimentSelectionPort {
            override fun resolveForLaunch(
              repoRoot: Path,
              parameter: String?,
              mode: ExperimentExecutionMode,
              savedSelection: List<String>?,
            ): ExperimentLaunchSelection =
              ExperimentLaunchSelection(
                normalizedNames = listOf("fixture-navigation"),
                descriptors = listOf("fixture-navigation"),
                availabilitySummary = "explicit",
              )
          },
        sessionRunner =
          object : ExperimentNavigationSessionRunnerPort {
            override fun runSession(request: ExperimentNavigationSessionRequest): ExperimentNavigationSessionResult {
              requests += request
              return ExperimentNavigationSessionResult(
                outcome = ExperimentNavigationTerminalOutcome.SEARCH_COMPLETED,
                deliveredPaths = emptyList(),
                shortlistedPaths = emptyList(),
              )
            }
          },
        gitOperations = gitOperations,
        pairOwner = InMemoryPairOwner(),
      )

    val pairId =
      coordinator.run(
        navigationRequest(
          specBytes = "## Acceptance Criteria\n1. Find the file.".toByteArray(),
        ),
      )

    assertEquals(listOf("resolved-commit", "resolved-commit"), requests.map { it.revision })
    assertEquals(listOf("Find the file.", "Find the file."), requests.flatMap { it.acceptanceCriteria })
    assertEquals(listOf(false, true), requests.map { it.treatmentEnabled })
    assertEquals(2, requests.map { it.repoRoot }.distinct().size)
    assertNotEquals(Path.of("."), requests.first().repoRoot)
  }

  @Test
  fun `resume does not replay a completed arm`() {
    val requests = mutableListOf<ExperimentNavigationSessionRequest>()
    val owner = InMemoryPairOwner()
    owner.save(
      ExperimentPairPersistedState(
        pairId = "pair-resume",
        executionMode = ExperimentExecutionMode.NAVIGATION,
        selectedNames = listOf("fixture-navigation"),
        armOrder =
          listOf(
            ExperimentArmId.CONTROL,
            ExperimentArmId.TREATMENT,
          ),
        randomSeed = "pair-resume",
        pairPayload =
          mapOf(
            ExperimentPairPayloadKeys.PAIR_ID to "pair-resume",
            ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-navigation"),
            ExperimentPairPayloadKeys.ARM_ORDER to listOf("control", "treatment"),
            ExperimentPairPayloadKeys.RANDOM_SEED to "pair-resume",
            ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY to
              mapOf(
                ExperimentPairPayloadKeys.REPOSITORY_IDENTITY to "repo",
                ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA to "resolved-commit",
                ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH to sha256Hex("spec".toByteArray()),
              ),
            ExperimentPairPayloadKeys.ARM_OUTCOMES to
              listOf(
                mapOf(
                  ExperimentPairPayloadKeys.ARM_ID to "control",
                  ExperimentPairPayloadKeys.TERMINAL_STATUS to "completed",
                ),
              ),
          ),
      ),
    )
    val coordinator =
      coordinator(
        requests,
        owner,
        RecordingWorkflowGitOperations().also {
          it.onResolveCommit = { WorkflowGitOperationResult.Ok(value = "resolved-commit") }
          it.repositoryFingerprintSequence.addAll(listOf("repo", "repo", "repo"))
          it.worktreeStatusValue = ""
        },
      )

    coordinator.run(navigationRequest("spec".toByteArray(), "pair-resume"))

    assertEquals(listOf("treatment"), requests.map { it.armId })
  }

  @Test
  fun `source identity drift is rejected before an arm starts`() {
    val requests = mutableListOf<ExperimentNavigationSessionRequest>()
    val gitOperations =
      RecordingWorkflowGitOperations().also {
        it.onResolveCommit = { WorkflowGitOperationResult.Ok(value = "resolved-commit") }
        it.repositoryFingerprintSequence.addAll(listOf("repo", "changed"))
        it.worktreeStatusValue = ""
      }
    val coordinator =
      coordinator(
        requests,
        InMemoryPairOwner(),
        gitOperations,
      )

    assertFailsWith<ExperimentNavigationRevisionError> {
      coordinator.run(navigationRequest("spec".toByteArray()))
    }

    assertEquals(emptyList(), requests)
  }

  @Test
  fun `cancellation persists the arm and prevents the other arm from starting`() {
    val requests = mutableListOf<ExperimentNavigationSessionRequest>()
    val owner = InMemoryPairOwner()
    val coordinator =
      coordinator(
        requests,
        owner,
        RecordingWorkflowGitOperations().also {
          it.onResolveCommit = { WorkflowGitOperationResult.Ok(value = "resolved-commit") }
          it.repositoryFingerprintSequence.addAll(listOf("repo", "repo", "repo"))
          it.worktreeStatusValue = ""
        },
        outcome = ExperimentNavigationTerminalOutcome.CANCELLED,
      )

    val pairId = coordinator.run(navigationRequest("spec".toByteArray()))

    assertEquals(listOf("control"), requests.map { it.armId })
    assertEquals("cancelled", owner.load(pairId)?.pairPayload?.get(ExperimentPairPayloadKeys.PAIR_STATUS))
  }

  @Test
  fun `a live pair lease refuses a second navigation recovery before a session starts`() {
    val requests = mutableListOf<ExperimentNavigationSessionRequest>()
    val owner = InMemoryPairOwner().also { it.leaseAvailable = false }
    val coordinator =
      coordinator(
        requests,
        owner,
        RecordingWorkflowGitOperations(),
      )

    assertFailsWith<ExperimentIsolationCapabilityRefusalError> {
      coordinator.run(navigationRequest("spec".toByteArray()))
    }

    assertEquals(emptyList(), requests)
  }

  private fun navigationRequest(
    specBytes: ByteArray = "spec".toByteArray(),
    pairId: String? = null,
  ): ExperimentNavigationPairRequest =
    ExperimentNavigationPairRequest(
      source =
        ExperimentNavigationPairSource(
          name = "fixture-navigation",
          repoRoot = Path.of("."),
          revision = "main",
          specBytes = specBytes,
          criteria = listOf("Find the file."),
        ),
      pairId = pairId,
    )

  private fun coordinator(
    requests: MutableList<ExperimentNavigationSessionRequest>,
    owner: InMemoryPairOwner,
    gitOperations: RecordingWorkflowGitOperations,
    outcome: ExperimentNavigationTerminalOutcome = ExperimentNavigationTerminalOutcome.SEARCH_COMPLETED,
  ): ExperimentNavigationPairCoordinator =
    ExperimentNavigationPairCoordinator(
      selectionPort =
        object : ExperimentSelectionPort {
          override fun resolveForLaunch(
            repoRoot: Path,
            parameter: String?,
            mode: ExperimentExecutionMode,
            savedSelection: List<String>?,
          ): ExperimentLaunchSelection =
            ExperimentLaunchSelection(
              normalizedNames = listOf("fixture-navigation"),
              descriptors = listOf("fixture-navigation"),
              availabilitySummary = "explicit",
            )
        },
      sessionRunner =
        object : ExperimentNavigationSessionRunnerPort {
          override fun runSession(request: ExperimentNavigationSessionRequest): ExperimentNavigationSessionResult {
            requests += request
            return ExperimentNavigationSessionResult(
              outcome = outcome,
              deliveredPaths = emptyList(),
              shortlistedPaths = emptyList(),
            )
          }
        },
      gitOperations = gitOperations,
      pairOwner = owner,
    )

  private class InMemoryPairOwner : ExperimentPairOwnerPort {
    private val states = mutableMapOf<String, ExperimentPairPersistedState>()
    var leaseAvailable = true

    override fun load(pairId: String): ExperimentPairPersistedState? = states[pairId]

    override fun save(state: ExperimentPairPersistedState) {
      states[state.pairId] = state
    }

    override fun importObservation(payload: Map<String, Any?>): Boolean = true

    override fun acquireLease(
      pairId: String,
      ownerToken: String,
      nowEpochMillis: Long,
      leaseMillis: Long,
    ): Boolean = leaseAvailable
  }

  private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
      "%02x".format(byte)
    }
}
