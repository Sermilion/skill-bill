package skillbill.infrastructure.sqlite

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.learnings.LearningsRuntime
import skillbill.learnings.model.CreateLearningRequest
import skillbill.learnings.model.LearningRecord
import skillbill.learnings.model.LearningScope
import skillbill.learnings.model.LearningSourceValidation
import skillbill.learnings.model.UpdateLearningRequest
import java.sql.Connection
import java.sql.ResultSet
import skillbill.infrastructure.sqlite.core.bindAll

internal object SQLiteLearningStore {
  fun addLearning(
    connection: Connection,
    request: CreateLearningRequest,
    sourceValidation: LearningSourceValidation,
  ): Int {
    val (validatedScope, validatedScopeKey) =
      LearningsRuntime.validateLearningScope(request.scope, request.scopeKey)
    val (validatedTitle, validatedRuleText) =
      LearningsRuntime.validateLearningText(request.title, request.ruleText)
    val requestedSource =
      LearningsRuntime.validateLearningSourceReference(
        request.sourceReviewRunId,
        request.sourceFindingId,
      )
    require(
      requestedSource.reviewRunId == sourceValidation.reviewRunId &&
        requestedSource.findingId == sourceValidation.findingId,
    ) {
      "Learning source validation must match the requested source."
    }
    val effectiveRationale =
      LearningsRuntime.effectiveRationale(
        rationale = request.rationale,
        rejectedOutcomeNote = sourceValidation.rejectedOutcome.note,
      )

    connection.prepareStatement(
      """
      INSERT INTO learnings (
        scope,
        scope_key,
        title,
        rule_text,
        rationale,
        status,
        source_review_run_id,
        source_finding_id
      ) VALUES (?, ?, ?, ?, ?, 'active', ?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(validatedScope.wireName, validatedScopeKey, validatedTitle, validatedRuleText, effectiveRationale, sourceValidation.reviewRunId, sourceValidation.findingId)
      statement.executeUpdate()
    }
    return connection.createStatement().use { statement ->
      statement.executeQuery("SELECT last_insert_rowid()").use { resultSet ->
        resultSet.next()
        resultSet.getInt(1)
      }
    }
  }

  fun getLearning(connection: Connection, learningId: Int): LearningRecord = connection.prepareStatement(
    learningRecordSelectSql("WHERE id = ?"),
  ).use { statement ->
    statement.bindAll(learningId)
    statement.executeQuery().use { resultSet ->
      require(resultSet.next()) { "Unknown learning id '$learningId'." }
      resultSet.toLearningRecord()
    }
  }

  fun listLearnings(connection: Connection, status: String): List<LearningRecord> {
    val query =
      buildString {
        appendLine(learningRecordSelectSql())
        if (status != "all") {
          appendLine("WHERE status = ?")
        }
        append("ORDER BY id")
      }
    return connection.prepareStatement(query).use { statement ->
      if (status != "all") {
        statement.bindAll(status)
      }
      statement.executeQuery().use { resultSet ->
        buildList {
          while (resultSet.next()) {
            add(resultSet.toLearningRecord())
          }
        }
      }
    }
  }

  fun resolveLearnings(
    connection: Connection,
    repoScopeKey: String?,
    skillName: String?,
  ): Triple<String?, String?, List<LearningRecord>> {
    val normalizedRepoScopeKey = LearningsRuntime.normalizeOptionalLookupValue(repoScopeKey, "--repo")
    val normalizedSkillName = LearningsRuntime.normalizeOptionalLookupValue(skillName, "--skill")
    val scopeClauses = mutableListOf("scope = '${LearningScope.GLOBAL.wireName}'")
    val parameters = mutableListOf<String>()
    if (normalizedRepoScopeKey != null) {
      scopeClauses += "(scope = '${LearningScope.REPO.wireName}' AND scope_key = ?)"
      parameters += normalizedRepoScopeKey
    }
    if (normalizedSkillName != null) {
      scopeClauses += "(scope = '${LearningScope.SKILL.wireName}' AND scope_key = ?)"
      parameters += normalizedSkillName
    }

    val rows =
      connection.prepareStatement(
        """
        ${learningRecordSelectSql()}
        WHERE status = 'active'
          AND (${scopeClauses.joinToString(" OR ")})
        ORDER BY
          ${learningScopeOrderClause("scope")},
          id
        """.trimIndent(),
      ).use { statement ->
        statement.bindAll(*parameters.toTypedArray())
        statement.executeQuery().use { resultSet ->
          buildList {
            while (resultSet.next()) {
              add(resultSet.toLearningRecord())
            }
          }
        }
      }
    return Triple(normalizedRepoScopeKey, normalizedSkillName, rows)
  }

  fun editLearning(connection: Connection, request: UpdateLearningRequest): LearningRecord {
    val current = getLearning(connection, request.learningId)
    val nextScope = request.scope ?: LearningScope.fromWireName(current.scope)
    val nextScopeKey = request.scopeKey ?: current.scopeKey
    val (validatedScope, validatedScopeKey) = LearningsRuntime.validateLearningScope(nextScope, nextScopeKey)
    val (nextTitle, nextRuleText) =
      LearningsRuntime.validateLearningText(
        title = request.title ?: current.title,
        ruleText = request.ruleText ?: current.ruleText,
      )
    val nextRationale = request.rationale?.trim() ?: current.rationale

    connection.prepareStatement(
      """
      UPDATE learnings
      SET scope = ?,
          scope_key = ?,
          title = ?,
          rule_text = ?,
          rationale = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(validatedScope.wireName, validatedScopeKey, nextTitle, nextRuleText, nextRationale, request.learningId)
      statement.executeUpdate()
    }
    return getLearning(connection, request.learningId)
  }

  fun setLearningStatus(connection: Connection, learningId: Int, status: String): LearningRecord {
    val validatedStatus = LearningsRuntime.validateLearningStatus(status)
    getLearning(connection, learningId)
    connection.prepareStatement(
      """
      UPDATE learnings
      SET status = ?, updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(validatedStatus, learningId)
      statement.executeUpdate()
    }
    return getLearning(connection, learningId)
  }

  fun deleteLearning(connection: Connection, learningId: Int) {
    getLearning(connection, learningId)
    connection.prepareStatement("DELETE FROM learnings WHERE id = ?").use { statement ->
      statement.bindAll(learningId)
      statement.executeUpdate()
    }
  }

  fun countLearnings(connection: Connection, status: String? = null): Int {
    val query =
      if (status == null) {
        "SELECT COUNT(*) FROM learnings"
      } else {
        "SELECT COUNT(*) FROM learnings WHERE status = ?"
      }
    return connection.prepareStatement(query).use { statement ->
      if (status != null) {
        statement.bindAll(status)
      }
      statement.executeQuery().use { resultSet ->
        if (resultSet.next()) {
          resultSet.getInt(1)
        } else {
          0
        }
      }
    }
  }

  fun saveSessionLearnings(connection: Connection, reviewSessionId: String, learningsJson: String) {
    connection.prepareStatement(
      """
      INSERT INTO session_learnings (review_session_id, learnings_json, updated_at)
      VALUES (?, ?, CURRENT_TIMESTAMP)
      ON CONFLICT(review_session_id) DO UPDATE SET
        learnings_json = excluded.learnings_json,
        updated_at = CURRENT_TIMESTAMP
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(reviewSessionId, learningsJson)
      statement.executeUpdate()
    }
  }

  fun fetchSessionLearnings(connection: Connection, reviewSessionId: String): Map<String, Any?>? =
    connection.prepareStatement(
      """
      SELECT learnings_json
      FROM session_learnings
      WHERE review_session_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(reviewSessionId)
      statement.executeQuery().use { resultSet ->
        if (!resultSet.next()) {
          return null
        }
        decodeSessionLearnings(resultSet.getString("learnings_json"))
      }
    }
}

private fun learningRecordSelectSql(whereClause: String? = null): String = buildString {
  appendLine("SELECT")
  appendLine("  id,")
  appendLine("  scope,")
  appendLine("  scope_key,")
  appendLine("  title,")
  appendLine("  rule_text,")
  appendLine("  rationale,")
  appendLine("  status,")
  appendLine("  source_review_run_id,")
  appendLine("  source_finding_id,")
  appendLine("  created_at,")
  appendLine("  updated_at")
  appendLine("FROM learnings")
  if (whereClause != null) {
    append(whereClause)
  }
}

private fun learningScopeOrderClause(columnName: String): String = buildString {
  appendLine("CASE $columnName")
  LearningScope.precedence.forEachIndexed { index, scope ->
    appendLine("  WHEN '${scope.wireName}' THEN $index")
  }
  append("  ELSE ${LearningScope.precedence.size}\nEND")
}

private fun decodeSessionLearnings(rawJson: String): Map<String, Any?>? = JsonCodec.parseObjectOrNull(rawJson)?.let {
  JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(it))
}

private fun ResultSet.toLearningRecord(): LearningRecord = LearningRecord(
  id = getInt("id"),
  scope = getString("scope"),
  scopeKey = getString("scope_key"),
  title = getString("title"),
  ruleText = getString("rule_text"),
  rationale = getString("rationale").orEmpty(),
  status = getString(SharedPayloadKeys.STATUS),
  sourceReviewRunId = getString("source_review_run_id"),
  sourceFindingId = getString("source_finding_id"),
  createdAt = getString("created_at"),
  updatedAt = getString("updated_at"),
)
