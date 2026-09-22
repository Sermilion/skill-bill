package skillbill.contracts.goalplanning

import skillbill.error.shellcontent.InvalidGoalVerificationBoundaryCapsSchemaError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoalVerificationBoundaryCapsTest {
  @Test
  fun `effective verification caps load from the packaged contract document`() {
    val document =
      GoalVerificationBoundaryCaps::class.java.classLoader
        .getResourceAsStream(GoalVerificationBoundaryCaps.RESOURCE_PATH)
        ?.use { stream -> stream.readBytes().decodeToString() }
        ?: error("goal verification boundary caps contract is missing from the classpath")
    val parsed = GoalVerificationBoundaryCaps.parse(document)
    assertEquals(parsed.maxDiscoveryFileCount, GoalVerificationBoundaryCaps.maxDiscoveryFileCount)
    assertEquals(parsed.maxHeadingsPerFile, GoalVerificationBoundaryCaps.maxHeadingsPerFile)
    assertEquals(parsed.maxCatalogHeadings, GoalVerificationBoundaryCaps.maxCatalogHeadings)
    assertEquals(parsed.historyRecencyDays, GoalVerificationBoundaryCaps.historyRecencyDays)
    assertEquals(parsed.maxSelectedBodies, GoalVerificationBoundaryCaps.maxSelectedBodies)
    assertEquals(parsed.maxBodyBytes, GoalVerificationBoundaryCaps.maxBodyBytes)
    assertEquals(parsed.maxTotalBodyBytes, GoalVerificationBoundaryCaps.maxTotalBodyBytes)
    assertEquals(parsed.maxBoundaryFileBytes, GoalVerificationBoundaryCaps.maxBoundaryFileBytes)
  }

  @Test
  fun `fractional and overflow cap values are rejected instead of coerced`() {
    val base =
      """
      contract_version: "0.2"
      max_discovery_file_count: 10
      max_headings_per_file: 10
      max_catalog_headings: 10
      history_recency_days: 10
      max_selected_bodies: 10
      max_body_bytes: 10
      max_total_body_bytes: 10
      max_boundary_file_bytes: 10
      """.trimIndent()
    assertFailsWith<InvalidGoalVerificationBoundaryCapsSchemaError> {
      GoalVerificationBoundaryCaps.parse(
        base.replace("max_discovery_file_count: 10", "max_discovery_file_count: 1.9"),
      )
    }
    assertFailsWith<InvalidGoalVerificationBoundaryCapsSchemaError> {
      GoalVerificationBoundaryCaps.parse(
        base.replace("max_discovery_file_count: 10", "max_discovery_file_count: 4294967297"),
      )
    }
    assertFailsWith<InvalidGoalVerificationBoundaryCapsSchemaError> {
      GoalVerificationBoundaryCaps.parse(
        base.replace("max_discovery_file_count: 10", "max_discovery_file_count: 0"),
      )
    }
    assertFailsWith<InvalidGoalVerificationBoundaryCapsSchemaError> {
      GoalVerificationBoundaryCaps.parse(
        base.replace("max_discovery_file_count: 10", "max_discovery_file_count: .nan"),
      )
    }
  }

  @Test
  fun `Long-backed boundary bytes retain the Long range without narrowing`() {
    val base =
      """
      contract_version: "0.2"
      max_discovery_file_count: 10
      max_headings_per_file: 10
      max_catalog_headings: 10
      history_recency_days: 10
      max_selected_bodies: 10
      max_body_bytes: 10
      max_total_body_bytes: 10
      max_boundary_file_bytes: 9223372036854775807
      """.trimIndent()

    assertEquals(Long.MAX_VALUE, GoalVerificationBoundaryCaps.parse(base).maxBoundaryFileBytes)
    assertFailsWith<InvalidGoalVerificationBoundaryCapsSchemaError> {
      GoalVerificationBoundaryCaps.parse(
        base.replace(
          "max_boundary_file_bytes: 9223372036854775807",
          "max_boundary_file_bytes: 9223372036854775808",
        ),
      )
    }
    assertFailsWith<InvalidGoalVerificationBoundaryCapsSchemaError> {
      GoalVerificationBoundaryCaps.parse(
        base.replace(
          "max_boundary_file_bytes: 9223372036854775807",
          "max_boundary_file_bytes: 9.223372036854776E18",
        ),
      )
    }
  }
}
