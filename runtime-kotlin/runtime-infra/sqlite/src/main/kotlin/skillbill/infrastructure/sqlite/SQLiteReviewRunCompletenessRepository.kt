package skillbill.infrastructure.sqlite

import skillbill.infrastructure.sqlite.review.stage.fetchFindingVerdicts
import skillbill.infrastructure.sqlite.review.stage.fetchReviewPassClaims
import skillbill.infrastructure.sqlite.review.stage.fetchSpecProjectionReference
import skillbill.infrastructure.sqlite.review.stage.fetchStageBoundaries
import skillbill.infrastructure.sqlite.review.stage.lane.fetchIntegrationPass
import skillbill.infrastructure.sqlite.review.stage.lane.fetchReviewRunLanes
import skillbill.infrastructure.sqlite.review.stage.lane.queryReviewLaneEffectiveness
import skillbill.infrastructure.sqlite.review.stage.lane.recordFindingLaneAttribution
import skillbill.infrastructure.sqlite.review.stage.lane.recordIntegrationPass
import skillbill.infrastructure.sqlite.review.stage.lane.replaceReviewRunLanes
import skillbill.infrastructure.sqlite.review.stage.recordFindingVerdicts
import skillbill.infrastructure.sqlite.review.stage.recordReviewPassClaims
import skillbill.infrastructure.sqlite.review.stage.recordSpecProjectionReference
import skillbill.infrastructure.sqlite.review.stage.recordStageBoundary
import skillbill.infrastructure.sqlite.review.stage.telemetry.ensureTerminalReviewState
import skillbill.ports.review.model.ReviewIntegrationPassRecord
import skillbill.ports.review.repository.ReviewRunCompletenessRepository
import skillbill.ports.review.repository.ReviewRunLaneCompletenessRepository
import skillbill.ports.review.repository.ReviewRunStageCompletenessRepository
import skillbill.review.model.ParallelReviewMergedFinding
import skillbill.review.model.ReviewExecutionMode
import skillbill.review.model.ReviewFindingVerdict
import skillbill.review.model.ReviewLaneEffectivenessRow
import skillbill.review.model.ReviewPassClaimSnapshot
import skillbill.review.model.ReviewRunLane
import skillbill.review.model.ReviewSpecProjectionReference
import skillbill.review.model.ReviewStageBoundary
import java.sql.Connection
import java.time.Clock
internal class SQLiteReviewRunLaneCompletenessRepository(
  private val connection: Connection,
) : ReviewRunLaneCompletenessRepository {
  override fun replaceReviewRunLanes(runId: String, lanes: List<ReviewRunLane>) =
    replaceReviewRunLanes(connection, runId, lanes)

  override fun fetchReviewRunLanes(runId: String): List<ReviewRunLane> = fetchReviewRunLanes(connection, runId)

  override fun recordFindingLaneAttribution(runId: String, attribution: Map<String, String>) =
    recordFindingLaneAttribution(connection, runId, attribution)

  override fun reviewLaneEffectiveness(runId: String?): List<ReviewLaneEffectivenessRow> =
    queryReviewLaneEffectiveness(connection, runId)

  override fun ensureTerminalReviewState(runId: String, executionMode: String?) =
    ensureTerminalReviewState(connection, runId, ReviewExecutionMode.fromWire(executionMode))

  override fun recordIntegrationPass(runId: String, record: ReviewIntegrationPassRecord) =
    recordIntegrationPass(connection, runId, record)

  override fun fetchIntegrationPass(runId: String): ReviewIntegrationPassRecord? =
    fetchIntegrationPass(connection, runId)
}

internal class SQLiteReviewRunStageCompletenessRepository(
  private val connection: Connection,
  private val clock: Clock,
) : ReviewRunStageCompletenessRepository {
  override fun recordFindingVerdicts(runId: String, verdicts: List<ReviewFindingVerdict>) =
    recordFindingVerdicts(connection, runId, verdicts)

  override fun fetchFindingVerdicts(runId: String): List<ReviewFindingVerdict> = fetchFindingVerdicts(connection, runId)

  override fun recordReviewPassClaims(runId: String, findings: List<ParallelReviewMergedFinding>) =
    recordReviewPassClaims(connection, clock, runId, findings)

  override fun fetchReviewPassClaims(runId: String): ReviewPassClaimSnapshot? = fetchReviewPassClaims(connection, runId)

  override fun recordStageBoundary(runId: String, boundary: ReviewStageBoundary) =
    recordStageBoundary(connection, runId, boundary)

  override fun fetchStageBoundaries(runId: String): List<ReviewStageBoundary> = fetchStageBoundaries(connection, runId)

  override fun recordSpecProjectionReference(runId: String, reference: ReviewSpecProjectionReference) =
    recordSpecProjectionReference(connection, clock, runId, reference)

  override fun fetchSpecProjectionReference(runId: String): ReviewSpecProjectionReference? =
    fetchSpecProjectionReference(connection, runId)
}

internal class SQLiteReviewRunCompletenessRepository(
  connection: Connection,
  clock: Clock,
) : ReviewRunCompletenessRepository,
  ReviewRunLaneCompletenessRepository by SQLiteReviewRunLaneCompletenessRepository(connection),
  ReviewRunStageCompletenessRepository by SQLiteReviewRunStageCompletenessRepository(connection, clock)
