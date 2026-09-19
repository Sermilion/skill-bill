package skillbill.infrastructure.sqlite.review.stage
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.infrastructure.sqlite.core.ops.bindAll
import skillbill.infrastructure.sqlite.review.stage.lane.reserveReviewRun
import skillbill.review.model.ParallelReviewMergedFinding
import skillbill.review.model.ReviewClaimVerdict
import skillbill.review.model.ReviewFindingVerdict
import skillbill.review.model.ReviewPassClaimSnapshot
import skillbill.review.model.ReviewScopeDisposition
import skillbill.review.model.ReviewSeverityAdjustment
import skillbill.review.model.ReviewSeverityAdjustmentDirection
import skillbill.review.model.ReviewSpecProjectionReference
import skillbill.review.model.ReviewStage
import skillbill.review.model.ReviewStageBoundary
import skillbill.review.model.ReviewStageReached
import java.sql.Connection
import java.time.Clock

internal fun recordFindingVerdicts(connection: Connection, reviewRunId: String, verdicts: List<ReviewFindingVerdict>) {
  reserveReviewRun(connection, reviewRunId)
  connection.prepareStatement(
    """
    INSERT INTO review_run_finding_verdicts (
      review_run_id,
      finding_id,
      stage,
      claim_verdict,
      scope_disposition,
      citations,
      severity_adjustment_direction,
      severity_adjustment_justification,
      recorded_at,
      contract_version,
      rejection_reason
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(review_run_id, finding_id, stage) DO UPDATE SET
      claim_verdict = excluded.claim_verdict,
      scope_disposition = excluded.scope_disposition,
      citations = excluded.citations,
      severity_adjustment_direction = excluded.severity_adjustment_direction,
      severity_adjustment_justification = excluded.severity_adjustment_justification,
      recorded_at = excluded.recorded_at,
      contract_version = excluded.contract_version,
      rejection_reason = excluded.rejection_reason
    """.trimIndent(),
  ).use { statement ->
    verdicts.forEach { verdict ->
      statement.bindAll(
        reviewRunId,
        verdict.findingRef,
        verdict.stage.wireValue,
        verdict.claimVerdict.wireValue,
        verdict.scopeDisposition?.wireValue,
        encodeCitations(verdict.citations),
        verdict.severityAdjustment?.direction?.wireValue,
        verdict.severityAdjustment?.justification,
        verdict.recordedAt,
        verdict.contractVersion,
        verdict.rejectionReason,
      )
      statement.executeUpdate()
    }
  }
}

internal fun fetchFindingVerdicts(connection: Connection, reviewRunId: String): List<ReviewFindingVerdict> =
  connection.prepareStatement(
    """
    SELECT finding_id, stage, claim_verdict, scope_disposition, citations,
           severity_adjustment_direction, severity_adjustment_justification,
           recorded_at, contract_version, rejection_reason
    FROM review_run_finding_verdicts
    WHERE review_run_id = ?
    ORDER BY stage, finding_id
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeQuery().use { resultSet ->
      buildList {
        while (resultSet.next()) {
          val direction = resultSet.getString("severity_adjustment_direction")
          val justification = resultSet.getString("severity_adjustment_justification")
          add(
            ReviewFindingVerdict(
              stage = ReviewStage.fromWire(resultSet.getString("stage")),
              findingRef = resultSet.getString(ReviewFindingPayloadKeys.FINDING_ID),
              claimVerdict = ReviewClaimVerdict.fromWire(resultSet.getString(ReviewFindingPayloadKeys.CLAIM_VERDICT)),
              scopeDisposition = resultSet.getString(ReviewFindingPayloadKeys.SCOPE_DISPOSITION)
                ?.let(ReviewScopeDisposition::fromWire),
              citations = decodeCitations(resultSet.getString(ReviewFindingPayloadKeys.CITATIONS)),
              severityAdjustment = if (direction == null || justification == null) {
                null
              } else {
                ReviewSeverityAdjustment(
                  ReviewSeverityAdjustmentDirection.fromWire(direction),
                  justification,
                )
              },
              recordedAt = resultSet.getString("recorded_at"),
              contractVersion = resultSet.getString(SharedPayloadKeys.CONTRACT_VERSION),
              rejectionReason = resultSet.getString("rejection_reason"),
            ),
          )
        }
      }
    }
  }

internal fun recordReviewPassClaims(
  connection: Connection,
  clock: Clock,
  reviewRunId: String,
  findings: List<ParallelReviewMergedFinding>,
) {
  val existing = fetchReviewPassClaims(connection, reviewRunId)
  if (findings.isEmpty() && existing != null && existing.findings.isNotEmpty()) return
  reserveReviewRun(connection, reviewRunId)
  connection.prepareStatement(
    """
    INSERT INTO review_run_pass_claims (review_run_id, claims_json, recorded_at)
    VALUES (?, ?, ?)
    ON CONFLICT(review_run_id) DO UPDATE SET
      claims_json = excluded.claims_json,
      recorded_at = excluded.recorded_at
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(
      reviewRunId,
      encodePassClaims(findings),
      clock.instant().toString(),
    )
    statement.executeUpdate()
  }
}

internal fun fetchReviewPassClaims(connection: Connection, reviewRunId: String): ReviewPassClaimSnapshot? =
  connection.prepareStatement(
    """
    SELECT claims_json
    FROM review_run_pass_claims
    WHERE review_run_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeQuery().use { resultSet ->
      if (!resultSet.next()) return null
      ReviewPassClaimSnapshot(decodePassClaims(resultSet.getString("claims_json")))
    }
  }

internal fun recordStageBoundary(connection: Connection, reviewRunId: String, boundary: ReviewStageBoundary) {
  reserveReviewRun(connection, reviewRunId)
  connection.prepareStatement(
    """
    INSERT INTO review_run_stage_boundaries (
      review_run_id, stage, reached, recorded_at, contract_version
    ) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(review_run_id, stage) DO UPDATE SET
      reached = excluded.reached,
      recorded_at = excluded.recorded_at,
      contract_version = excluded.contract_version
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(
      reviewRunId,
      boundary.stage.wireValue,
      boundary.reached.wireValue,
      boundary.recordedAt,
      boundary.contractVersion,
    )
    statement.executeUpdate()
  }
}

internal fun fetchStageBoundaries(connection: Connection, reviewRunId: String): List<ReviewStageBoundary> =
  connection.prepareStatement(
    """
    SELECT stage, reached, recorded_at, contract_version
    FROM review_run_stage_boundaries
    WHERE review_run_id = ?
    ORDER BY stage
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeQuery().use { resultSet ->
      buildList {
        while (resultSet.next()) {
          add(
            ReviewStageBoundary(
              stage = ReviewStage.fromWire(resultSet.getString("stage")),
              reached = ReviewStageReached.fromWire(resultSet.getString("reached")),
              recordedAt = resultSet.getString("recorded_at"),
              contractVersion = resultSet.getString(SharedPayloadKeys.CONTRACT_VERSION),
            ),
          )
        }
      }
    }
  }

internal fun recordSpecProjectionReference(
  connection: Connection,
  clock: Clock,
  reviewRunId: String,
  reference: ReviewSpecProjectionReference,
) {
  reserveReviewRun(connection, reviewRunId)
  connection.prepareStatement(
    """
    INSERT INTO review_run_spec_projections (
      review_run_id, spec_path, content_digest, absence_reason, recorded_at
    ) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(review_run_id) DO UPDATE SET
      spec_path = excluded.spec_path,
      content_digest = excluded.content_digest,
      absence_reason = excluded.absence_reason,
      recorded_at = excluded.recorded_at
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(
      reviewRunId,
      reference.specPath,
      reference.contentDigest,
      reference.absenceReason,
      clock.instant()
        .toString(),
    )
    statement.executeUpdate()
  }
}

internal fun fetchSpecProjectionReference(connection: Connection, reviewRunId: String): ReviewSpecProjectionReference? =
  connection.prepareStatement(
    """
    SELECT spec_path, content_digest, absence_reason
    FROM review_run_spec_projections
    WHERE review_run_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeQuery().use { resultSet ->
      if (!resultSet.next()) return null
      ReviewSpecProjectionReference(
        specPath = resultSet.getString("spec_path"),
        contentDigest = resultSet.getString("content_digest"),
        absenceReason = resultSet.getString("absence_reason"),
      )
    }
  }
