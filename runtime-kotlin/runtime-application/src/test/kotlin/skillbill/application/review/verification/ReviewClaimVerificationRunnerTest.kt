package skillbill.application.review.verification
import skillbill.application.review.model.ReviewClaimVerificationRunRequest
import skillbill.application.review.model.ReviewDelegatedStageLaunch
import skillbill.application.review.packet.commitUnits
import skillbill.application.review.packet.envelope
import skillbill.application.review.packet.index
import skillbill.application.review.packet.launch
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.claim.findings
import skillbill.application.review.parallel.core.code.review.claim.pack
import skillbill.application.review.parallel.core.code.review.end.repoRoot
import skillbill.application.review.parallel.core.code.review.end.run
import skillbill.application.review.parallel.core.code.review.evidence.index
import skillbill.application.review.parallel.core.code.review.inline.findings
import skillbill.application.review.parallel.core.code.review.inline.run
import skillbill.application.review.parallel.core.code.review.integration.pack
import skillbill.application.review.parallel.core.code.review.pass.index
import skillbill.application.review.parallel.core.code.review.regression.repoRoot
import skillbill.application.review.parallel.core.code.review.runner.agentIds
import skillbill.application.review.parallel.core.code.review.runner.all
import skillbill.application.review.parallel.core.code.review.runner.budget
import skillbill.application.review.parallel.core.code.review.runner.citationDiagnostics
import skillbill.application.review.parallel.core.code.review.runner.findings
import skillbill.application.review.parallel.core.code.review.runner.launch
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.runner.run
import skillbill.application.review.parallel.core.code.review.spec.REFUTED
import skillbill.application.review.parallel.core.code.review.spec.pack
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.code.review.stage.pack
import skillbill.application.review.parallel.core.code.review.standalone.pack
import skillbill.application.review.parallel.core.review.launch
import skillbill.application.review.parallel.core.review.run
import skillbill.application.review.parallel.planning.baseRevision
import skillbill.application.review.parallel.planning.budget
import skillbill.application.review.parallel.planning.headRevision
import skillbill.application.review.parallel.planning.originLayerChains
import skillbill.application.review.parallel.planning.ownedPaths
import skillbill.application.review.parallel.planning.repoRoot
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.parallel.planning.routingMatrix
import skillbill.application.review.parallel.planning.stack
import skillbill.application.review.parallel.verification.line
import skillbill.application.review.parallel.verification.verdicts
import skillbill.application.review.preparation.label
import skillbill.application.review.preparation.laneDecisions
import skillbill.application.review.preparation.launch
import skillbill.application.review.preparation.ownedPaths
import skillbill.application.review.preparation.request
import skillbill.application.review.preparation.routingMatrix
import skillbill.application.review.preparation.validator
import skillbill.application.review.review.budget
import skillbill.application.review.review.exitStatus
import skillbill.application.review.review.launcher
import skillbill.application.review.review.repoRoot
import skillbill.application.review.review.spawnFailed
import skillbill.application.review.review.timedOut
import skillbill.application.review.review.verdicts
import skillbill.application.review.service.review
import skillbill.application.review.spec.brokerId
import skillbill.application.review.spec.budget
import skillbill.application.review.spec.citationDiagnostics
import skillbill.application.review.spec.envelope
import skillbill.application.review.spec.existingVerdicts
import skillbill.application.review.spec.findings
import skillbill.application.review.spec.launch
import skillbill.application.review.spec.launcher
import skillbill.application.review.spec.repoRoot
import skillbill.application.review.spec.run
import skillbill.application.review.spec.timeout
import skillbill.application.review.spec.validator
import skillbill.application.testHarnessClock
import skillbill.install.model.InstallAgent
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.review.context.ReviewContextEnvelopeValidator
import skillbill.review.context.model.commit.ReviewCommitCoverageFact
import skillbill.review.context.model.commit.ReviewCommitLaneDecision
import skillbill.review.context.model.commit.ReviewCommitLaneDisposition
import skillbill.review.context.model.commit.ReviewCommitLaneRoutingMatrix
import skillbill.review.context.model.commit.ReviewCommitSource
import skillbill.review.context.model.commit.ReviewCommitUnit
import skillbill.review.context.model.execution.ResolvedReviewExecutionMode
import skillbill.review.context.model.execution.ReviewLaneDecision
import skillbill.review.context.model.hunk.ReviewChangedHunk
import skillbill.review.context.model.hunk.ReviewContextBudgetPolicy
import skillbill.review.context.model.hunk.ReviewDependencyAllowlist
import skillbill.review.context.model.hunk.ReviewRevision
import skillbill.review.context.model.packet.ReviewContextPacket
import skillbill.review.context.model.packet.ReviewPacketConsumerContract
import skillbill.review.model.ParallelReviewMergedFinding
import skillbill.review.model.ParallelReviewSeverity
import skillbill.review.model.ReviewClaimVerdict
import skillbill.review.model.ReviewFindingVerdict
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ReviewClaimVerificationRunnerTest {
  @Test
  fun `each finding launches alone without siblings narrative or parent transcript`() {
    val launches = mutableListOf<GoalRunnerSubtaskLaunchRequest>()
    val envelopes = mutableListOf<Map<String, Any?>>()
    val outcome = runner(
      launcher = { request ->
        launches += request
        facts(request, CONFIRMED)
      },
      validator = { envelope, _ -> envelopes += envelope },
    ).run(
      verificationRequest(
        findings = listOf(finding("F-001"), finding("F-002", "src/B.kt:4", "other bug")),
        repoRoot = Files.createTempDirectory("verify-isolation"),
      ),
    )
    assertEquals(2, launches.size)
    assertEquals(2, envelopes.size)
    envelopes.forEach { envelope ->
      assertEquals("verification_launch", envelope["kind"])
      assertFalse(envelope.containsKey("spec_intent_projection"))
      assertFalse(envelope.containsKey("narrative"))
      assertFalse(envelope.containsKey("transcript"))
      assertFalse(envelope.containsKey("parent_transcript"))
      val finding = envelope["finding"] as Map<*, *>
      assertEquals(setOf("finding_ref", "severity", "location", "description", "confidence"), finding.keys)
    }
    val refs = envelopes.map { (it["finding"] as Map<*, *>)["finding_ref"] }
    assertEquals(listOf("F-001", "F-002"), refs)
    launches.forEachIndexed { index, launch ->
      val prompt = launch.skillRunRequest.promptOverride.orEmpty()
      val own = refs[index] as String
      val other = refs[1 - index] as String
      assertTrue(own in prompt)
      assertFalse(other in prompt)
    }
    assertEquals(listOf("F-001", "F-002"), outcome.verdicts.map { it.findingRef })
    assertTrue(outcome.verdicts.all { it.claimVerdict == ReviewClaimVerdict.CONFIRMED })
  }

  @Test
  fun `a worker that fails to spawn times out or returns unparseable output leaves the finding unresolved`() {
    val responses = ArrayDeque(
      listOf(
        factsFor { copy(spawnFailed = true, exitStatus = null, stdout = "") },
        factsFor { copy(timedOut = true, exitStatus = null, stdout = "") },
        factsFor { copy(stdout = "not a verdict") },
      ),
    )
    val outcome = runner(
      launcher = { request -> responses.removeFirst().invoke(request) },
    ).run(
      verificationRequest(
        findings = listOf(
          finding("F-001"),
          finding("F-002", "src/B.kt:4", "second"),
          finding("F-003", "src/C.kt:8", "third"),
        ),
        mode = ResolvedReviewExecutionMode.DELEGATED,
        repoRoot = Files.createTempDirectory("verify-failure"),
      ),
    )
    assertEquals(3, outcome.verdicts.size)
    assertTrue(outcome.verdicts.all { it.claimVerdict == ReviewClaimVerdict.UNRESOLVED })
    assertEquals("agent process failed to spawn", outcome.verdicts[0].rejectionReason)
    assertEquals("agent timed out", outcome.verdicts[1].rejectionReason)
    assertEquals("unparseable verification output", outcome.verdicts[2].rejectionReason)
  }

  @Test
  fun `zero worker citation lines are coerced while the finding verdict still settles`() {
    val stdout = """
      {
        "claim_verdict": "refuted",
        "citations": [
          {"path": "src/A.kt", "line": 12},
          {"path": "src/A.kt", "line": 0}
        ]
      }
    """.trimIndent()
    val outcome = runner(
      launcher = { request -> facts(request, stdout) },
    ).run(
      verificationRequest(
        findings = listOf(finding("F-001")),
        repoRoot = Files.createTempDirectory("verify-malformed-citation"),
      ),
    )
    assertEquals(ReviewClaimVerdict.REFUTED, outcome.verdicts.single().claimVerdict)
    assertEquals(
      listOf(12, 1),
      outcome.verdicts.single().citations.map { it.line },
    )
    assertTrue(outcome.citationDiagnostics.isEmpty())
  }

  @Test
  fun `inline bounds evidence to the cited region while delegated permits brokered expansion`() {
    val inline = envelopesFor(ResolvedReviewExecutionMode.INLINE)
    val delegated = envelopesFor(ResolvedReviewExecutionMode.DELEGATED)
    val inlineRules = inline.single()["evidence_surface_rules"] as String
    val delegatedRules = delegated.single()["evidence_surface_rules"] as String
    assertEquals(ReviewPacketConsumerContract.INLINE_VERIFICATION_EVIDENCE_SURFACE, inlineRules)
    assertEquals(ReviewPacketConsumerContract.DELEGATED_VERIFICATION_EVIDENCE_SURFACE, delegatedRules)
    assertTrue("Cited region and direct callers" in inlineRules)
    assertFalse("expansion ledger is permitted" in inlineRules)
    assertTrue("expansion ledger is permitted" in delegatedRules)
    assertEquals(inline.single()["finding"], delegated.single()["finding"])
    assertFalse(inline.single().containsKey("spec_intent_projection"))
    assertFalse(delegated.single().containsKey("spec_intent_projection"))
  }

  private fun envelopesFor(mode: ResolvedReviewExecutionMode): List<Map<String, Any?>> {
    val envelopes = mutableListOf<Map<String, Any?>>()
    runner(
      launcher = { request -> facts(request, CONFIRMED) },
      validator = { envelope, _ -> envelopes += envelope },
    ).run(
      verificationRequest(
        findings = listOf(finding("F-001")),
        mode = mode,
        repoRoot = Files.createTempDirectory("verify-depth"),
      ),
    )
    return envelopes
  }

  private fun verificationRequest(
    findings: List<ParallelReviewMergedFinding>,
    existingVerdicts: List<ReviewFindingVerdict> = emptyList(),
    mode: ResolvedReviewExecutionMode = ResolvedReviewExecutionMode.INLINE,
    repoRoot: Path = Files.createTempDirectory("verify"),
  ) = ReviewClaimVerificationRunRequest(
    packet = packet(),
    findings = findings,
    existingVerdicts = existingVerdicts,
    mode = mode,
    launch = ReviewDelegatedStageLaunch(
      budget = ReviewContextBudgetPolicy.DEFAULT,
      brokerId = "codex",
      repoRoot = repoRoot,
      timeout = 1.seconds,
    ),
  )

  private fun runner(
    launcher: GoalRunnerSubtaskLauncher,
    validator: ReviewContextEnvelopeValidator = ReviewContextEnvelopeValidator { _, _ -> },
  ) = ReviewClaimVerificationRunner(launcher, validator, testHarnessClock)

  private fun facts(request: GoalRunnerSubtaskLaunchRequest, stdout: String) = AgentRunLaunchFacts(
    agent = InstallAgent.fromNormalizedId(request.invokedAgentId, label = "agentId"),
    exitStatus = 0,
    stdout = stdout,
    stderr = "",
    timedOut = false,
    spawnFailed = false,
  )

  private fun factsFor(
    mutate: AgentRunLaunchFacts.() -> AgentRunLaunchFacts,
  ): (GoalRunnerSubtaskLaunchRequest) -> AgentRunLaunchFacts = { request ->
    facts(request, CONFIRMED).mutate()
  }

  private fun finding(ref: String, location: String = "src/A.kt:12", description: String = "Null is not checked.") =
    ParallelReviewMergedFinding(
      fNumber = ref,
      agentIds = listOf("codex"),
      severity = ParallelReviewSeverity.MAJOR,
      confidence = "High",
      location = location,
      description = description,
      repositoryPath = location.substringBefore(':'),
      line = location.substringAfter(':').toInt(),
    )

  private fun packet(): ReviewContextPacket {
    val hunk = ReviewChangedHunk("src/A.kt", 1, 1, 1, 2, "+alpha")
    val lanes = listOf("security")
    return ReviewContextPacket(
      reviewId = "review",
      repositoryIdentity = "repo",
      baseRevision = "base",
      headRevision = "head",
      status = "clean",
      stack = "kotlin",
      pack = "kotlin",
      addOns = emptyList(),
      selectedLanes = lanes,
      changedHunks = listOf(hunk),
      commitUnits = listOf(
        ReviewCommitUnit("head", "base", "change", 0, listOf(hunk), ReviewCommitSource.COMMIT_RANGE),
      ),
      coverageFact = ReviewCommitCoverageFact("base", "head", 1, chainVerified = true, pathCoverageVerified = true),
      routingMatrix = ReviewCommitLaneRoutingMatrix(
        listOf("head"),
        lanes,
        listOf(ReviewCommitLaneDecision("head", 0, "security", ReviewCommitLaneDisposition.FOCUSED, "focused")),
      ),
      reviewRevision = ReviewRevision("rvs-1", 1),
      laneDecisions = listOf(
        ReviewLaneDecision(
          "security",
          true,
          "routed",
          ownedPaths = listOf("src/A.kt"),
          originLayerChains = listOf(listOf("kotlin")),
          owningPack = "kotlin",
          specialistSkillName = "bill-kotlin-code-review-security",
        ),
      ),
      dependencyAllowlist = ReviewDependencyAllowlist(listOf("src/Dep.kt")),
    )
  }

  private companion object {
    const val CONFIRMED: String = """{"claim_verdict":"confirmed"}"""
  }
}
