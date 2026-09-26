package skillbill.engine.featuretask.slotbaseline

import skillbill.application.review.governed.stubGovernedReviewEvidenceEndpointBinder
import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.snapshot.diffForPaths
import skillbill.application.review.snapshot.harnessRequest
import skillbill.application.review.snapshot.parallelCodeReviewRunnerOf
import skillbill.application.review.snapshot.simulateGovernedEvidenceReads
import skillbill.application.review.snapshot.sparseReviewPack
import skillbill.application.review.spec.SpecIntentProjectionExtractor
import skillbill.application.review.spec.SpecIntentProjectionResolver
import skillbill.config.model.RepoLocalConfig
import skillbill.infrastructure.contracts.review.ReviewContextSchemaValidator
import skillbill.infrastructure.contracts.workflow.decomposition.DecompositionManifestSchemaValidator
import skillbill.infrastructure.workflow.decomposition.FileSystemDecompositionManifestFileStore
import skillbill.infrastructure.workflow.review.broker.FileSystemReviewEvidenceBroker
import skillbill.infrastructure.workflow.review.specialists.ClasspathReviewSpecialistContractProvider
import skillbill.install.model.SupportedAgent
import skillbill.ports.agentrun.agentRunLaunchFacts
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import skillbill.ports.config.model.ReadRepoLocalConfigResult
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.review.evidence.ReviewEvidenceBrokerFactory
import skillbill.ports.review.model.ResolvedReviewRubric
import skillbill.ports.review.model.ReviewCheckpointFileIdentity
import skillbill.ports.review.model.ReviewOwnedFileEvidence
import skillbill.ports.review.preparation.ReviewRubricResolver
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.scaffold.model.PlatformManifest
import skillbill.workflow.model.goalreview.toReviewAccountingBoundedJson
import java.nio.file.Path

internal object SlotBaselineCodeReviewCapture {
  private const val REVIEWED_PATH = "runtime-kotlin/runtime-engine/src/test/kotlin/Example.kt"
  private const val PARENT_REVIEW_ISSUE_KEY = "code-review"

  private val pack =
    sparseReviewPack(
      slug = "kotlin",
      requiredArea = "architecture",
      pathAreas = mapOf("testing" to listOf("src/test/")),
    )

  fun encodedFiles(): Map<String, String> {
    val inline = captureMode(CodeReviewExecutionMode.INLINE, "inln")
    val delegated = captureMode(CodeReviewExecutionMode.DELEGATED, "dlgt")
    return mapOf(
      SlotBaselinePaths.INLINE_OUTPUT to inline.output,
      SlotBaselinePaths.DELEGATED_OUTPUT to delegated.output,
      SlotBaselinePaths.REVIEW_RUNS_INLINE to inline.reviewRuns,
      SlotBaselinePaths.REVIEW_RUNS_DELEGATED to delegated.reviewRuns,
      SlotBaselinePaths.REVIEW_TELEMETRY_INLINE to inline.telemetry,
      SlotBaselinePaths.REVIEW_TELEMETRY_DELEGATED to delegated.telemetry,
    ).entries.associate { (fileName, value) ->
      "${SlotBaselinePaths.CODE_REVIEW}/$fileName" to SlotBaselineJson.encode(value)
    }
  }

  private fun captureMode(
    mode: CodeReviewExecutionMode,
    runSuffix: String,
  ): ReviewModeCapture {
    val repoRoot = SlotBaselineFullRunCapture.seededRepoRoot()
    val home = SlotBaselineNormalizer.newTempHome()
    try {
      val database = SlotBaselineFullRunCapture.sqliteDatabase(home)
      val parentProse =
        when (mode) {
          CodeReviewExecutionMode.DELEGATED -> "NO_FINDINGS"
          else -> "Inline slot baseline review.\nverdict: approved"
        }
      val envelopeValidator = ReviewContextSchemaValidator()
      val runner =
        parallelCodeReviewRunnerOf(
          diffResolver = FixedDiffResolver(diffForPaths(REVIEWED_PATH)),
          repoLocalConfig = DefaultRepoLocalConfig,
          reviewContextEnvelopeValidator = envelopeValidator,
          reviewRubricResolver = GovernedRubricResolver,
          reviewSpecialistContractProvider = ClasspathReviewSpecialistContractProvider(),
          database = database,
          installedPackCatalog = InstalledPlatformPackCatalogPort { listOf(pack) },
          specIntentProjectionResolver =
            SpecIntentProjectionResolver(
              FileSystemDecompositionManifestFileStore(),
              DecompositionManifestSchemaValidator(),
              SpecIntentProjectionExtractor(envelopeValidator, FileSystemDecompositionManifestFileStore()),
            ),
          parentReviewLauncher =
            GoalRunnerSubtaskLauncher { request ->
              simulateGovernedEvidenceReads(request.skillRunRequest)
              agentRunLaunchFacts(
                agent = SupportedAgent.fromNormalizedId(request.invokedAgentId, label = "agentId"),
                stdout =
                  if (request.skillRunRequest.issueKey == PARENT_REVIEW_ISSUE_KEY) parentProse else "NO_FINDINGS",
                stderr = "",
              )
            },
          reviewEvidenceBrokerFactory = ReviewEvidenceBrokerFactory(::FileSystemReviewEvidenceBroker),
          governedEvidenceEndpointBinder = stubGovernedReviewEvidenceEndpointBinder(home.resolve("review-endpoint")),
          clock = SlotBaselineFullRunCapture.sqliteClock,
        )
      val result =
        runner.run(
          harnessRequest(
            repoRoot = repoRoot,
            reviewRunId = "rvw-20260602-120000-$runSuffix",
            codeReviewMode = mode,
          ).copy(reviewSessionId = "rvs-slot-baseline-$runSuffix"),
        )
      val databasePath = database.resolveDbPath()
      return ReviewModeCapture(
        output = result.printedFields(),
        reviewRuns = SlotBaselineSqlite.tablesWithPrefix(databasePath, "review_"),
        telemetry = SlotBaselineSqlite.rows(databasePath, "telemetry_outbox"),
      )
    } finally {
      repoRoot.toFile().deleteRecursively()
      home.toFile().deleteRecursively()
    }
  }

  private fun ParallelCodeReviewResult.printedFields(): Map<String, Any?> =
    mapOf(
      "output" to output,
      "lane1" to
        mapOf(
          "agent_id" to lane1.agentId,
          "success" to lane1.success,
          "failure_reason" to lane1.failureReason,
          "dropped_candidate_diagnostic" to lane1.droppedCandidateDiagnostic,
        ),
      "review_session_id" to reviewSessionId,
      "applied_learnings" to appliedLearnings,
      "coverage" to coverage?.render(),
      "accounting_summary" to
        accountingSummary?.toReviewAccountingBoundedJson()?.let(SlotBaselineJson::parseEmbedded),
    )

  private data class ReviewModeCapture(
    val output: Map<String, Any?>,
    val reviewRuns: Map<String, List<Map<String, Any?>>>,
    val telemetry: List<Map<String, Any?>>,
  )
}

private class FixedDiffResolver(private val diff: String) : DiffResolverPort {
  override fun reviewWorktreeFileIdentities(
    root: Path,
    paths: List<String>,
  ): Map<String, ReviewCheckpointFileIdentity> = emptyMap()

  override fun readDiff(
    path: Path,
    maxBytes: Long,
  ): String? = null

  override fun runProcess(
    args: List<String>,
    workDir: Path,
  ): String =
    when (args.getOrNull(1)) {
      "rev-parse" -> args.last().removeSuffix("^{commit}")
      "rev-list" -> ""
      else -> diff
    }
}

private object DefaultRepoLocalConfig : RepoLocalConfigPort {
  override fun readRepoLocalConfig(request: ReadRepoLocalConfigRequest) =
    ReadRepoLocalConfigResult(RepoLocalConfig.defaults())
}

private object GovernedRubricResolver : ReviewRubricResolver {
  override fun resolve(manifest: PlatformManifest?): ResolvedReviewRubric =
    ResolvedReviewRubric("parallel-code-review", "governed rubric body for parallel-code-review")

  override fun resolve(
    manifest: PlatformManifest?,
    evidence: List<ReviewOwnedFileEvidence>,
    specialistSkillName: String,
  ): ResolvedReviewRubric =
    ResolvedReviewRubric(
      rubricId = specialistSkillName,
      body = "governed rubric body for $specialistSkillName",
      area = specialistSkillName.substringAfter("-code-review-", "generic"),
    )
}
