package skillbill.infrastructure.sqlite.review
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.review.ReviewParser
import skillbill.review.model.FindingMetadata
import skillbill.review.model.ImportedFinding
import skillbill.review.model.ImportedReview
import skillbill.review.model.NumberedFinding
import skillbill.review.model.ReviewSummary
import java.sql.Connection
import skillbill.infrastructure.sqlite.core.bindAll
import skillbill.contracts.review.SqliteReviewTelemetryPayloadKeys
internal object ReviewRuntime {
  fun parseReview(text: String): ImportedReview = ReviewParser.parseReview(text)

  fun fetchImportedFindings(connection: Connection, reviewRunId: String): List<ImportedFinding> =
    connection.prepareStatement(importedFindingsSql).use { statement ->
      statement.bindAll(reviewRunId)
      statement.executeQuery().use { resultSet ->
        buildList {
          while (resultSet.next()) {
            add(resultSet.toImportedFinding())
          }
        }
      }
    }

  fun fetchReviewSummary(connection: Connection, reviewRunId: String): ReviewSummary =
    connection.prepareStatement(reviewSummarySql).use { statement ->
      statement.bindAll(reviewRunId)
      statement.executeQuery().use { resultSet ->
        require(resultSet.next()) { "Unknown review run id '$reviewRunId'." }
        resultSet.toReviewSummary()
      }
    }

  fun fetchFindingMetadata(connection: Connection, reviewRunId: String, findingId: String): FindingMetadata =
    connection.prepareStatement(findingMetadataSql).use { statement ->
      statement.bindAll(reviewRunId, findingId)
      statement.executeQuery().use { resultSet ->
        require(resultSet.next()) { "Unknown finding id '$findingId' for review run '$reviewRunId'." }
        FindingMetadata(
          findingId = resultSet.getString(ReviewFindingPayloadKeys.FINDING_ID),
          severity = resultSet.getString(SqliteReviewTelemetryPayloadKeys.SEVERITY),
          confidence = resultSet.getString(SqliteReviewTelemetryPayloadKeys.CONFIDENCE),
        )
      }
    }

  fun fetchNumberedFindings(connection: Connection, reviewRunId: String): List<NumberedFinding> {
    require(reviewExists(connection, reviewRunId)) { "Unknown review run id '$reviewRunId'." }
    return connection.prepareStatement(numberedFindingsSql).use { statement ->
      statement.bindAll(reviewRunId)
      statement.executeQuery().use { resultSet ->
        buildList {
          var index = 1
          while (resultSet.next()) {
            add(resultSet.toNumberedFinding(index++))
          }
        }
      }
    }
  }

  fun reviewExists(connection: Connection, reviewRunId: String): Boolean = connection.prepareStatement(
    "SELECT 1 FROM review_runs WHERE review_run_id = ? AND raw_text != ''",
  ).use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeQuery().use { resultSet -> resultSet.next() }
  }

  fun findingExists(connection: Connection, reviewRunId: String, findingId: String): Boolean =
    connection.prepareStatement("SELECT 1 FROM findings WHERE review_run_id = ? AND finding_id = ?").use { statement ->
      statement.bindAll(reviewRunId, findingId)
      statement.executeQuery().use { resultSet -> resultSet.next() }
    }
}
