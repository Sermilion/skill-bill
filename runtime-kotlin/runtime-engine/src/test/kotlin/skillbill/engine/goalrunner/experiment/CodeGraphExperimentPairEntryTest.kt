package skillbill.engine.goalrunner.experiment

import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.engine.RecordingWorkflowGitOperations
import skillbill.engine.goalrunner.model.GoalRunnerRunRequest
import skillbill.experiment.model.ExperimentArmId
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.goalrunner.model.GoalPullRequestStatus
import skillbill.goalrunner.model.GoalRunnerRunReport
import skillbill.infrastructure.host.experiment.codegraph.InMemoryCodeGraphUsageLedger
import skillbill.infrastructure.host.experiment.codegraph.ProcessCodeGraphRetrievalAdapter
import skillbill.model.EnvironmentContext
import skillbill.ports.experiment.codegraph.CodeGraphPairSetupPort
import skillbill.ports.experiment.codegraph.model.CodeGraphInstalledTool
import skillbill.ports.experiment.codegraph.model.CodeGraphPairSetupRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphPairSetupResult
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.ports.experiment.isolation.ExperimentArmIsolationContext
import skillbill.ports.experiment.isolation.ExperimentIsolationCapabilityPort
import skillbill.ports.experiment.measurement.ExperimentArmMeasurement
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.measurement.ExperimentMeasuredValue
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPersistedState
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.experiment.publication.ExperimentPublicationResult
import skillbill.ports.experiment.selection.ExperimentLaunchSelection
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeGraphExperimentPairEntryTest {
  @Test
  fun `public pair entry provisions codegraph once denies control and publishes comparison report`() {
    val fixture = pairEntryFixture()
    runPair(fixture)
    assertPairOutcome(fixture)
  }

  private data class PairEntryFixture(
    val repository: Path,
    val owner: InMemoryOwner,
    val requests: MutableList<GoalRunnerRunRequest>,
    val provisionedPairs: MutableList<String>,
    val setupRequests: MutableList<CodeGraphPairSetupRequest>,
    val releasedPairs: MutableList<String>,
    val deliveryCalls: MutableList<Triple<String, String, Boolean>>,
    val retrievalCalls: MutableList<ExperimentArmId>,
    val coordinator: ExperimentPairCoordinator,
  )

  private data class CoordinatorFixtureInputs(
    val repository: Path,
    val owner: InMemoryOwner,
    val requests: MutableList<GoalRunnerRunRequest>,
    val retrievalPort: ProcessCodeGraphRetrievalAdapter,
    val retrievalCalls: MutableList<ExperimentArmId>,
    val deliveryCalls: MutableList<Triple<String, String, Boolean>>,
  )

  private fun pairEntryFixture(): PairEntryFixture {
    val repository = fixtureRepository()
    val owner = InMemoryOwner()
    val requests = mutableListOf<GoalRunnerRunRequest>()
    val provisionedPairs = mutableListOf<String>()
    val setupRequests = mutableListOf<CodeGraphPairSetupRequest>()
    val releasedPairs = mutableListOf<String>()
    val deliveryCalls = mutableListOf<Triple<String, String, Boolean>>()
    val retrievalCalls = mutableListOf<ExperimentArmId>()
    val retrievalPort = fixtureRetrievalPort(repository)
    val setupPort = fixtureSetupPort(repository, provisionedPairs, setupRequests, releasedPairs)
    val coordinator = fixtureCoordinator(
      CoordinatorFixtureInputs(repository, owner, requests, retrievalPort, retrievalCalls, deliveryCalls),
      setupPort,
    )
    return PairEntryFixture(
      repository,
      owner,
      requests,
      provisionedPairs,
      setupRequests,
      releasedPairs,
      deliveryCalls,
      retrievalCalls,
      coordinator,
    )
  }

  private fun fixtureRepository(): Path {
    val repository = Files.createTempDirectory("codegraph-pair-entry")
    val spec = repository.resolve(".feature-specs/SKILL-366/spec.md")
    Files.createDirectories(spec.parent)
    Files.writeString(spec, "frozen spec")
    val toolsBin = repository.resolve(".skill-bill/tools/codegraph/v1.6.0")
    Files.createDirectories(toolsBin)
    val binary = toolsBin.resolve("codegraph")
    Files.writeString(binary, fixtureBinaryScript())
    binary.toFile().setExecutable(true)
    return repository
  }

  private fun fixtureBinaryScript() = """
    #!/bin/sh
    if [ "${'$'}1" = "query" ]; then
      printf '[{"node":{"name":"Caller","filePath":"Caller.kt","kind":"call","startLine":1},"score":1}]'
    fi
  """.trimIndent()

  private fun fixtureRetrievalPort(repository: Path): ProcessCodeGraphRetrievalAdapter {
    val binary = repository.resolve(".skill-bill/tools/codegraph/v1.6.0/codegraph")
    return ProcessCodeGraphRetrievalAdapter(binary, InMemoryCodeGraphUsageLedger())
  }

  private fun fixtureSetupPort(
    repository: Path,
    provisionedPairs: MutableList<String>,
    setupRequests: MutableList<CodeGraphPairSetupRequest>,
    releasedPairs: MutableList<String>,
  ) = object : CodeGraphPairSetupPort {
    override fun provisionAfterConfirmation(request: CodeGraphPairSetupRequest): CodeGraphPairSetupResult {
      provisionedPairs += request.pairId
      setupRequests += request
      return CodeGraphPairSetupResult(
        provisioned = true,
        installedTool = CodeGraphInstalledTool(
          releaseTag = "v1.6.0",
          binaryPath = repository.resolve(".skill-bill/tools/codegraph/v1.6.0/codegraph"),
          platformId = "linux-x64",
          sha256 = "a".repeat(64),
        ),
        installDurationMs = 12,
      )
    }

    override fun releaseOwnedResources(pairId: String) {
      releasedPairs += pairId
    }
  }

  private fun fixtureCoordinator(inputs: CoordinatorFixtureInputs, setupPort: CodeGraphPairSetupPort) =
    ExperimentPairCoordinator(
      goalRunner = fixtureGoalRunner(inputs),
      selectionPort = codegraphSelectionPort(),
      pairOwner = inputs.owner,
      gitOperations = fixtureGitOperations(),
      isolationCapability = fixtureIsolationCapability(),
      measurementPort = fixtureMeasurementPort(),
      parentDelivery = ExperimentParentDeliveryPort { pair, workflow, _, completed, _ ->
        inputs.deliveryCalls += Triple(pair, workflow, completed)
        ExperimentPublicationResult(published = completed)
      },
      environmentContext = EnvironmentContext(userHome = inputs.repository),
      codeGraphPairSetup = setupPort,
    )

  private fun fixtureGoalRunner(inputs: CoordinatorFixtureInputs) = ExperimentGoalRunnerPort { request ->
    request.experimentArmId?.takeIf { it == ExperimentArmId.TREATMENT }?.let { arm ->
      val dependency = repositoryRoot().resolve("orchestration/dependencies/codegraph-dependency.yaml")
      Files.createDirectories(request.repoRoot.resolve("orchestration/dependencies"))
      Files.copy(dependency, request.repoRoot.resolve("orchestration/dependencies/codegraph-dependency.yaml"))
      Files.writeString(request.repoRoot.resolve("Caller.kt"), "class Caller\n")
      val result = inputs.retrievalPort.query(
        CodeGraphQueryRequest(
          worktreeRoot = request.repoRoot,
          queryText = request.issueKey,
          pairId = request.experimentPairId,
        ),
      )
      assertTrue(result.hits.any { it.file == "Caller.kt" })
      inputs.retrievalCalls += arm
    }
    inputs.requests += request
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
  }

  private fun runPair(fixture: PairEntryFixture) {
    fixture.coordinator.run(
      GoalRunnerRunRequest(
        issueKey = "SKILL-366",
        repoRoot = fixture.repository,
        invokedAgentId = "fixture-agent",
        experimentsParameter = "codegraph",
      ),
    )
    fixture.coordinator.run(
      GoalRunnerRunRequest(
        issueKey = "SKILL-366",
        repoRoot = fixture.repository,
        invokedAgentId = "fixture-agent",
        experimentPairId = fixture.requests.first().experimentPairId,
      ),
    )
  }

  private fun assertPairOutcome(fixture: PairEntryFixture) {
    assertSetup(fixture)
    assertArmIsolation(fixture)
    assertDelivery(fixture)
    assertFrozenIdentity(fixture)
    assertReport(fixture)
    assertTrue(fixture.releasedPairs.isNotEmpty())
  }

  private fun assertSetup(fixture: PairEntryFixture) {
    assertEquals(2, fixture.provisionedPairs.size)
    assertEquals(null, fixture.setupRequests.first().pinnedReleaseTag)
    assertEquals("v1.6.0", fixture.setupRequests.last().pinnedReleaseTag)
    assertEquals(
      "v1.6.0",
      fixture.owner.lastState?.pairPayload?.get(ExperimentPairPayloadKeys.CODEGRAPH_TOOL_RELEASE_TAG),
    )
    assertEquals(
      "managed",
      fixture.owner.lastState?.pairPayload?.get(ExperimentPairPayloadKeys.CODEGRAPH_TOOL_PROVENANCE),
    )
  }

  private fun assertArmIsolation(fixture: PairEntryFixture) {
    assertEquals(2, fixture.requests.size)
    assertEquals(listOf(ExperimentArmId.TREATMENT), fixture.retrievalCalls)
    assertEquals(
      setOf("codegraph"),
      fixture.requests.single { it.experimentArmId == ExperimentArmId.CONTROL }
        .experimentTreatmentCapabilitiesDenied,
    )
    assertEquals(
      emptySet(),
      fixture.requests.single { it.experimentArmId == ExperimentArmId.TREATMENT }
        .experimentTreatmentCapabilitiesDenied,
    )
    assertTrue(fixture.requests.all { it.experimentTreatmentCapabilities == setOf("codegraph") })
  }

  private fun assertDelivery(fixture: PairEntryFixture) {
    assertEquals(1, fixture.deliveryCalls.size)
    assertTrue(fixture.deliveryCalls.single().third)
    assertEquals(
      fixture.requests.first().experimentPairId,
      fixture.owner.lastReport?.get(ExperimentReportPayloadKeys.PAIR_ID),
    )
  }

  private fun assertFrozenIdentity(fixture: PairEntryFixture) {
    val frozenIdentity = fixture.owner.lastState?.pairPayload
      ?.get(ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY) as Map<*, *>
    assertEquals("repository", frozenIdentity[ExperimentPairPayloadKeys.REPOSITORY_IDENTITY])
    assertEquals("a".repeat(40), frozenIdentity[ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA])
    assertTrue(frozenIdentity[ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH]?.toString()?.isNotBlank() == true)
    assertTrue(frozenIdentity[ExperimentPairPayloadKeys.RUN_SETTINGS_HASH]?.toString()?.isNotBlank() == true)
  }

  private fun assertReport(fixture: PairEntryFixture) {
    assertTrue(
      (fixture.owner.lastReport?.get(ExperimentReportPayloadKeys.METRIC_COMPARISONS) as? List<*>)
        ?.isNotEmpty() == true,
    )
    val reportMetricIds = (fixture.owner.lastReport?.get(ExperimentReportPayloadKeys.METRIC_COMPARISONS) as? List<*>)
      .orEmpty()
      .filterIsInstance<Map<*, *>>()
      .mapNotNull { it[ExperimentReportPayloadKeys.METRIC_ID]?.toString() }
      .toSet()
    assertTrue(ExperimentObservationPayloadKeys.CODEGRAPH_INSTALL_DURATION_METRIC_ID in reportMetricIds)
  }

  private fun repositoryRoot(): Path {
    var current = Path.of(".").toAbsolutePath().normalize()
    while (true) {
      if (Files.isRegularFile(current.resolve("orchestration/dependencies/codegraph-dependency.yaml"))) {
        return current
      }
      val parent = current.parent ?: break
      current = parent
    }
    error("Expected skill-bill repository root.")
  }

  private fun codegraphSelectionPort() = object : ExperimentSelectionPort {
    override fun resolveForLaunch(
      repoRoot: Path,
      parameter: String?,
      mode: ExperimentExecutionMode,
      savedSelection: List<String>?,
    ) = ExperimentLaunchSelection(
      normalizedNames = listOf("codegraph"),
      descriptors = listOf("codegraph"),
      availabilitySummary = "explicit",
      treatmentCapabilities = setOf("codegraph"),
    )
  }

  private fun fixtureGitOperations() = RecordingWorkflowGitOperations().also {
    it.headCommitShaValue = "a".repeat(40)
    it.repositoryFingerprintValue = "repository"
    it.worktreeStatusValue = ""
  }

  private fun fixtureIsolationCapability() = object : ExperimentIsolationCapabilityPort {
    override fun assertLaunchSupported(context: ExperimentArmIsolationContext) = Unit
  }

  private fun fixtureMeasurementPort() = ExperimentArmMeasurementPort { _, armId, _ ->
    ExperimentArmMeasurement(
      setupCost = measured(1.0),
      usage = measured(if (armId == ExperimentArmId.TREATMENT.wireValue) 2.0 else 0.0),
      cost = measured(3.0),
      additionalMeasurements = mapOf(
        ExperimentObservationPayloadKeys.CODEGRAPH_INSTALL_DURATION_METRIC_ID to measured(4.0),
        ExperimentObservationPayloadKeys.CODEGRAPH_INDEX_DURATION_METRIC_ID to measured(5.0),
        ExperimentObservationPayloadKeys.CODEGRAPH_SYNC_DURATION_METRIC_ID to measured(6.0),
        ExperimentObservationPayloadKeys.CODEGRAPH_QUERY_COUNT_METRIC_ID to measured(2.0),
        ExperimentObservationPayloadKeys.CODEGRAPH_EVIDENCE_BYTES_METRIC_ID to measured(7.0),
      ),
    )
  }

  private fun measured(quantity: Double) = ExperimentMeasuredValue(
    quantity = quantity,
    availability = TelemetryMeasurementAvailability.MEASURED.wireValue,
  )

  private class InMemoryOwner : ExperimentPairOwnerPort {
    var lastState: ExperimentPairPersistedState? = null
    var lastReport: Map<String, Any?>? = null

    override fun save(state: ExperimentPairPersistedState) {
      lastState = state
    }

    override fun load(pairId: String): ExperimentPairPersistedState? = lastState?.takeIf { it.pairId == pairId }

    override fun saveReport(pairId: String, reportPayload: Map<String, Any?>) {
      lastReport = reportPayload
    }

    override fun importObservation(payload: Map<String, Any?>): Boolean {
      val state = lastState ?: return false
      val observations = (state.pairPayload[ExperimentPairPayloadKeys.OBSERVATION_LEDGER] as? List<*>)
        .orEmpty() + payload
      lastState = state.copy(
        pairPayload = state.pairPayload + (ExperimentPairPayloadKeys.OBSERVATION_LEDGER to observations),
      )
      return true
    }
  }
}
