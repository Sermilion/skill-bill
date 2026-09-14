package skillbill.architecture

import skillbill.contracts.workflow.DecompositionManifestSchemaPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireVocabularyArchitectureTest {
  @Test
  fun `runtime wire vocabulary has no duplicate declarations or local restatements`() {
    val report = WireVocabularyArchitectureSupport.scanRuntimeMainSources()
    assertEquals(
      emptyList(),
      report.violations,
      "Wire vocabulary baseline delta ${report.baselineViolationCount} -> " +
        "${report.remainingViolationCount}:\n${report.violations.joinToString("\n")}",
    )
    assertEquals(0, report.remainingViolationCount)
    assertEquals(report.baselineViolationCount, report.remainingViolationCount)
  }

  @Test
  fun `scanner accepts owners references typed subsets prose and open extension values`() {
    val files = listOf(
      syntheticSourceFile(
        "fixture/Owner.kt",
        """
        package fixture

        enum class Owner(val wireValue: String) {
          READY("ready"),

          companion object {
            fun fromWire(value: String): Owner? = when (value) {
              "old_ready" -> READY
              else -> entries.firstOrNull { it.wireValue == value }
            }
          }
        }

        object ContractKeys {
          const val STATUS = "status"
        }
        """.trimIndent(),
      ),
      syntheticSourceFile(
        "fixture/Consumer.kt",
        """
        package fixture

        import fixture.Owner as AliasOwner

        fun consume(value: AliasOwner) = value.wireValue
        fun subset(): Set<AliasOwner> = setOf(AliasOwner.READY)
        fun keyed(): Map<String, String> = mapOf(ContractKeys.STATUS to "value")
        fun prose(): String = "ready is a word in this sentence"
        fun extension(value: String): String = value
        """.trimIndent(),
      ),
    )
    val report = WireVocabularyArchitectureSupport.scanSourceFiles(files)
    assertEquals(emptyList(), report.violations)
    assertTrue(report.declarations.any { it.category == "token" && it.value == "ready" })
    assertTrue(report.declarations.any { it.category == "alias" && it.value == "old_ready" })
    assertTrue(report.declarations.any { it.category == "key" && it.value == "status" })
  }

  @Test
  fun `scanner keeps identical spellings separate when the decoding context differs`() {
    val files = listOf(
      syntheticSourceFile(
        "fixture/Owner.kt",
        """
        package fixture

        enum class Owner(val wireValue: String) {
          READY("ready"),
        }
        """.trimIndent(),
      ),
      syntheticSourceFile(
        "unrelated/Labels.kt",
        """
        package unrelated

        val labels = setOf("ready")
        """.trimIndent(),
      ),
      syntheticSourceFile(
        "consumer/Decoder.kt",
        """
        package consumer

        import fixture.Owner

        fun decode(value: Owner) = value.wireValue
        val labels = setOf("ready")
        """.trimIndent(),
      ),
    )
    val report = WireVocabularyArchitectureSupport.scanSourceFiles(files)
    assertTrue(report.violations.none { it.contains("unrelated/Labels.kt") })
    assertTrue(report.violations.any { it.contains("consumer/Decoder.kt") && it.contains("restates 'ready'") })
  }

  @Test
  fun `new schema field without kotlin owner fails through the production scanner`() {
    val report = WireVocabularyArchitectureSupport.scanSourceFiles(
      files = listOf(
        syntheticSourceFile(
          "workflow/decomposition/ManifestKeys.kt",
          """
          package fixture

          object ManifestKeys {
            const val STATUS: String = "status"
          }
          """.trimIndent(),
        ),
      ),
      enforceGovernedSeams = true,
      schemaPropertyKeysByPath = mapOf(
        DecompositionManifestSchemaPaths.REPO_RELATIVE_PATH to setOf("status", "schema_introduced_key"),
      ),
    )
    assertTrue(
      report.violations.any { it.contains("schema_introduced_key") },
      "Expected independent schema authority to flag a new field before its owner exists",
    )
  }

  @Test
  fun `governed seam rejects undeclared literal even when keys object declares the wire string`() {
    val files = listOf(
      syntheticSourceFile(
        "fixture/ManifestKeys.kt",
        """
        package fixture

        object ManifestKeys {
          const val STATUS: String = "status"
        }
        """.trimIndent(),
      ),
      syntheticSourceFile(
        "workflow/decomposition/ForeignConsumer.kt",
        """
        package fixture

        fun read(payload: Map<String, Any?>) = payload["status"]
        """.trimIndent(),
      ),
    )
    val report = WireVocabularyArchitectureSupport.scanSourceFiles(
      files,
      includePayloadKeyAccesses = true,
      enforceGovernedSeams = true,
      schemaPropertyKeysByPath = mapOf(
        DecompositionManifestSchemaPaths.REPO_RELATIVE_PATH to setOf("status"),
      ),
    )
    assertTrue(report.violations.any { it.contains("accesses key 'status'") })
  }

  @Test
  fun `governed seam accepts keys constant reference and open extension map values`() {
    val files = listOf(
      syntheticSourceFile(
        "fixture/ManifestKeys.kt",
        """
        package fixture

        object ManifestKeys {
          const val STATUS: String = "status"
        }
        """.trimIndent(),
      ),
      syntheticSourceFile(
        "workflow/decomposition/Codec.kt",
        """
        package fixture

        fun keyed(payload: Map<String, Any?>) = payload[ManifestKeys.STATUS]
        fun extensionValues(produced: Map<String, Any?>) = produced["custom_phase_output"]
        """.trimIndent(),
      ),
    )
    val report = WireVocabularyArchitectureSupport.scanSourceFiles(
      files,
      includePayloadKeyAccesses = true,
      enforceGovernedSeams = true,
      schemaPropertyKeysByPath = mapOf(
        DecompositionManifestSchemaPaths.REPO_RELATIVE_PATH to setOf("status"),
      ),
    )
    assertEquals(emptyList(), report.violations)
  }

  @Test
  fun `scanner rejects duplicates aliases local collections and foreign key accesses`() {
    val files = listOf(
      syntheticSourceFile(
        "fixture/Owner.kt",
        """
        package fixture

        enum class Owner(val wireValue: String) {
          FIRST("ready"),
          SECOND("ready"),

          companion object {
            fun fromWire(value: String): Owner? = when (value) {
              "old_ready" -> FIRST
              "old_ready" -> SECOND
              else -> null
            }
          }
        }

        object ContractKeys {
          const val STATUS = "status"
        }
        """.trimIndent(),
      ),
      syntheticSourceFile(
        "fixture/Consumer.kt",
        """
        package fixture

        @SerialName("status")
        val annotated = "value"
        val statuses = setOf("ready")
        fun consume(payload: Map<String, Any?>) = mapOf("ready" to payload["status"])
        """.trimIndent(),
      ),
    )
    val report = WireVocabularyArchitectureSupport.scanSourceFiles(files, includePayloadKeyAccesses = true)
    assertTrue(report.violations.any { it.contains("duplicate token 'ready'") })
    assertTrue(report.violations.any { it.contains("duplicate alias 'old_ready'") })
    assertTrue(report.violations.any { it.contains("restates 'ready'") })
    assertTrue(report.violations.any { it.contains("accesses key 'status'") })
    assertEquals(report.violations.sorted(), report.violations)
  }
}
