package skillbill.engine

import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceDeriver
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceResolverPort
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceDerivation
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceRequest
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceResolution
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceResolveOutcome
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffProjectionValue
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceDiffPayloadRef
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceFileEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceHunkEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceOutcome
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedReviewEvidenceReference
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeatureTaskRuntimeSharedEvidenceEndToEndTest {
  @Test
  fun `implement to audit to review shares exactly one derivation at an unchanged checkpoint`() {
    val repoRoot = createTempDirectory("shared-evidence-e2e")
    val store = CountingSharedEvidenceStore()
    val diffResolver = CountingDiffResolver()
    val launcher = defaultPhaseAwareLauncher()
    val harness = telemetryRunnerHarness(
      RuntimeHarnessConfig(
        repoRoot = repoRoot,
        launcher = launcher,
        sharedEvidenceResolver = store,
        diffResolver = diffResolver,
      ),
    )

    val report = harness.runner.run(harness.request)
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report, report.toString())
    assertUnchangedCheckpointSharing(harness, store, diffResolver, launcher)
    assertReviewLanesShareOneDerivation()
  }

  @Test
  fun `stateless audit records one checkpoint fingerprint for shared evidence`() {
    val repoRoot = createTempDirectory("shared-evidence-stateless-audit")
    val store = CountingSharedEvidenceStore()
    val git = RecordingWorkflowGitOperations().also {
      it.headCommitShaValue = "a".repeat(40)
      it.ownedPathsValue = listOf("src/A.kt")
    }
    val fingerprintsAtAudit = mutableListOf<String>()
    val harness = runnerHarness(
      RuntimeHarnessConfig(
        repoRoot = repoRoot,
        branchSetup = BranchSetupTestConfig(gitOperations = git),
        sharedEvidenceResolver = store,
        diffResolver = CountingDiffResolver(),
      ).copy(launcher = satisfiedAuditWithCheckpointLauncher(repoRoot, git, fingerprintsAtAudit)),
    )

    assertIs<FeatureTaskRuntimeRunReport.Completed>(harness.runner.run(harness.request()))
    assertEquals(1, fingerprintsAtAudit.size)
    assertTrue(store.derivationCount >= 1, "at least one derivation must occur")
  }

  @Test
  fun `a fingerprint hit never serves a stale artifact derived at another fingerprint`() {
    val store = CountingSharedEvidenceStore()
    val first = store.resolve(request("fp-old"), fixedDeriver("old"))
    val second = store.resolve(request("fp-new"), fixedDeriver("new"))
    assertNotEquals(first.artifact.fingerprint, second.artifact.fingerprint)
    assertEquals("old", first.artifact.baseRef)
    assertEquals("new", second.artifact.baseRef)
    val reused = store.resolve(request("fp-old")) { error("must not re-derive on hit") }
    assertEquals("fp-old", reused.artifact.fingerprint)
    assertEquals("old", reused.artifact.baseRef)
    assertEquals(FeatureTaskRuntimeSharedEvidenceResolveOutcome.REUSE, reused.outcome)
  }

  private fun assertUnchangedCheckpointSharing(
    harness: TelemetryRunnerHarness,
    store: CountingSharedEvidenceStore,
    diffResolver: CountingDiffResolver,
    launcher: RuntimeRecordingLauncher,
  ) {
    val briefings = assertNotNull(harness.recorder.loadPhaseBriefings(WORKFLOW_ID))
    val auditPrompt = launcher.requests.map { requireNotNull(it.skillRunRequest.promptOverride) }
      .single { phaseIdFromPrompt(it) == "audit" }
    assertTrue("audit" !in briefings)
    val reviewEvidence = assertNotNull(sharedEvidencePath(briefings.getValue("review")))
    assertContains(auditPrompt, reviewEvidence)

    val measurements = harness.lifecycle.sharedEvidenceMeasurements
    assertEquals(
      0,
      measurements.count { it.outcome == FeatureTaskRuntimeSharedEvidenceOutcome.DERIVATION },
      "audit derivation must not write stage telemetry: $measurements",
    )
    assertTrue(
      measurements.count { it.outcome == FeatureTaskRuntimeSharedEvidenceOutcome.REUSE } >= 1,
      "later consumers must reuse: $measurements",
    )
    assertEquals(
      setOf("review"),
      measurements.map { it.consumerPhaseId }.toSet(),
      "only review retains a shared-evidence measurement",
    )
    assertEquals(
      1,
      measurements.map { it.checkpointFingerprint }.toSet().size,
      "all consumers at an unchanged checkpoint must share one fingerprint",
    )
    assertEquals(1, store.derivationCount, "store must derive exactly once")
    assertTrue(
      diffResolver.invocations <= store.derivationCount,
      "reuse path must not re-traverse the repository beyond the single derivation",
    )
  }

  private fun assertReviewLanesShareOneDerivation() {
    val laneStore = CountingSharedEvidenceStore()
    val laneRequest = request("lane-checkpoint")
    val firstLane = laneStore.resolve(laneRequest, fixedDeriver("lane-base"))
    val laterLanes = (1..3).map {
      laneStore.resolve(laneRequest) { error("review lane must not re-derive on a fingerprint hit") }
    }
    assertEquals(1, laneStore.derivationCount, "review lanes must share exactly one derivation")
    assertTrue(
      laterLanes.all {
        it.storePath == firstLane.storePath &&
          it.artifact.fingerprint == firstLane.artifact.fingerprint
      },
      "every review lane bundle must resolve to the same stored artifact",
    )
  }

  private fun satisfiedAuditWithCheckpointLauncher(
    repoRoot: Path,
    git: RecordingWorkflowGitOperations,
    fingerprintsAtAudit: MutableList<String>,
  ): RuntimeRecordingLauncher = RuntimeRecordingLauncher { request ->
    when (val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))) {
      "audit" -> {
        val fingerprint = git.repositoryFingerprintOperations
          .repositoryCheckpointFingerprint(
            repoRoot,
            null,
            git.headCommitShaValue,
            git.ownedPathsValue,
          ).value.orEmpty()
        fingerprintsAtAudit += fingerprint
        facts(auditSatisfiedOutput())
      }
      else -> facts(validJsonOutputForGitPhase(phaseId, git))
    }
  }

  private fun sharedEvidencePath(briefing: FeatureTaskRuntimePhaseLaunchBriefing): String? =
    briefing.handoffEnvelope.projections
      .firstOrNull { it.projectionName == "shared_review_evidence" }
      ?.fields
      ?.firstOrNull { it.name == FeatureTaskRuntimeSharedReviewEvidenceReference.FIELD_STORE_PATH }
      ?.value
      ?.let { value ->
        when (value) {
          is FeatureTaskRuntimeHandoffProjectionValue.CompactReference -> value.value
          is FeatureTaskRuntimeHandoffProjectionValue.Text -> value.text
          else -> null
        }
      }

  private fun request(fingerprint: String) = FeatureTaskRuntimeSharedEvidenceRequest(
    repoRoot = Path.of("."),
    workflowId = WORKFLOW_ID,
    checkpoint = FeatureTaskRuntimeRepositoryCheckpoint(fingerprint),
  )

  private fun fixedDeriver(baseRef: String) = FeatureTaskRuntimeSharedEvidenceDeriver {
    FeatureTaskRuntimeSharedEvidenceDerivation(
      baseRef = baseRef,
      headRef = "head",
      files = listOf(FeatureTaskRuntimeSharedEvidenceFileEntry("src/A.kt", "modified")),
      hunks = listOf(FeatureTaskRuntimeSharedEvidenceHunkEntry("src/A.kt", "@@ -1 +1 @@")),
      diffPayload = "diff for $baseRef",
    )
  }
}

private class CountingDiffResolver(
  private val diff: String = "diff --git a/src/A.kt b/src/A.kt\n@@ -1 +1 @@\n+added\n",
) : DiffResolverPort {
  var invocations: Int = 0
    private set

  override fun runProcess(args: List<String>, workDir: Path): String {
    invocations++
    return diff
  }
}

internal class CountingSharedEvidenceStore : FeatureTaskRuntimeSharedEvidenceResolverPort {
  private val stored = mutableMapOf<String, FeatureTaskRuntimeSharedEvidenceResolution>()
  var derivationCount: Int = 0
    private set
  var reuseCount: Int = 0
    private set
  val outcomes = mutableListOf<FeatureTaskRuntimeSharedEvidenceResolveOutcome>()
  val servedFingerprints = mutableListOf<String>()

  override fun resolve(
    request: FeatureTaskRuntimeSharedEvidenceRequest,
    deriver: FeatureTaskRuntimeSharedEvidenceDeriver,
  ): FeatureTaskRuntimeSharedEvidenceResolution {
    val fingerprint = request.checkpoint.fingerprint
    stored[fingerprint]?.let { hit ->
      val reused = hit.copy(outcome = FeatureTaskRuntimeSharedEvidenceResolveOutcome.REUSE)
      reuseCount++
      outcomes += reused.outcome
      servedFingerprints += reused.artifact.fingerprint
      return reused
    }
    derivationCount++
    val derivation = deriver.derive(request.checkpoint)
    val outcome = if (stored.isNotEmpty()) {
      FeatureTaskRuntimeSharedEvidenceResolveOutcome.CHECKPOINT_CHANGE_REDERIVATION
    } else {
      FeatureTaskRuntimeSharedEvidenceResolveOutcome.DERIVATION
    }
    val resolution = FeatureTaskRuntimeSharedEvidenceResolution(
      artifact = FeatureTaskRuntimeSharedEvidenceArtifact(
        fingerprint = fingerprint,
        baseRef = derivation.baseRef,
        headRef = derivation.headRef,
        files = derivation.files,
        hunks = derivation.hunks,
        diffPayload = FeatureTaskRuntimeSharedEvidenceDiffPayloadRef(
          "diff.patch",
          derivation.diffPayload.length.toLong(),
        ),
      ),
      diffPayload = derivation.diffPayload,
      storePath = ".skill-bill/run-evidence/${request.workflowId}/$fingerprint",
      outcome = outcome,
    )
    stored[fingerprint] = resolution
    outcomes += outcome
    servedFingerprints += fingerprint
    return resolution
  }
}
