package skillbill.contracts.goalplanning

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.packaged.PackagedYamlMappingFailure
import skillbill.contracts.packaged.loadPackagedYamlRootMapping
import skillbill.contracts.packaged.packagedPositiveInt
import skillbill.contracts.packaged.packagedPositiveLong
import skillbill.error.InvalidGoalVerificationBoundaryCapsSchemaError

object GoalVerificationBoundaryCaps {
  const val CONTRACT_VERSION = "0.2"
  const val RESOURCE_PATH = "skillbill/infrastructure/contracts/goal-verification-boundary-caps.yaml"
  const val CONTRACT_FILE = "orchestration/contracts/goal-verification-boundary-caps.yaml"
  const val SCHEMA_FILE = "orchestration/contracts/goal-verification-boundary-caps-schema.yaml"

  private val KNOWN_KEYS = setOf(
    "contract_version",
    "max_discovery_file_count",
    "max_headings_per_file",
    "max_catalog_headings",
    "history_recency_days",
    "max_selected_bodies",
    "max_body_bytes",
    "max_total_body_bytes",
    "max_boundary_file_bytes",
  )

  private val contract: Contract by lazy { parse(readContract()) }

  val maxDiscoveryFileCount: Int get() = contract.maxDiscoveryFileCount
  val maxHeadingsPerFile: Int get() = contract.maxHeadingsPerFile
  val maxCatalogHeadings: Int get() = contract.maxCatalogHeadings
  val historyRecencyDays: Int get() = contract.historyRecencyDays
  val maxSelectedBodies: Int get() = contract.maxSelectedBodies
  val maxBodyBytes: Int get() = contract.maxBodyBytes
  val maxTotalBodyBytes: Int get() = contract.maxTotalBodyBytes
  val maxBoundaryFileBytes: Long get() = contract.maxBoundaryFileBytes

  internal data class Contract(
    val maxDiscoveryFileCount: Int,
    val maxHeadingsPerFile: Int,
    val maxCatalogHeadings: Int,
    val historyRecencyDays: Int,
    val maxSelectedBodies: Int,
    val maxBodyBytes: Int,
    val maxTotalBodyBytes: Int,
    val maxBoundaryFileBytes: Long,
  )

  private fun readContract(): String =
    javaClass.classLoader.getResourceAsStream(RESOURCE_PATH)?.use { stream -> stream.readBytes().decodeToString() }
      ?: throw InvalidGoalVerificationBoundaryCapsSchemaError(
        "goal verification boundary caps contract is missing from the classpath at $RESOURCE_PATH",
      )

  internal fun parse(document: String): Contract {
    val root = loadRootMapping(document)
    requireKnownKeysOnly(root)
    requireSupportedVersion(root[SharedPayloadKeys.CONTRACT_VERSION])
    return Contract(
      maxDiscoveryFileCount = requiredPositiveInt(root, "max_discovery_file_count"),
      maxHeadingsPerFile = requiredPositiveInt(root, "max_headings_per_file"),
      maxCatalogHeadings = requiredPositiveInt(root, "max_catalog_headings"),
      historyRecencyDays = requiredPositiveInt(root, "history_recency_days"),
      maxSelectedBodies = requiredPositiveInt(root, "max_selected_bodies"),
      maxBodyBytes = requiredPositiveInt(root, "max_body_bytes"),
      maxTotalBodyBytes = requiredPositiveInt(root, "max_total_body_bytes"),
      maxBoundaryFileBytes = requiredPositiveLong(root, "max_boundary_file_bytes"),
    )
  }

  private fun loadRootMapping(document: String): Map<*, *> = try {
    loadPackagedYamlRootMapping(
      document,
      "goal verification boundary caps contract is not a YAML mapping",
    )
  } catch (_: PackagedYamlMappingFailure) {
    throw InvalidGoalVerificationBoundaryCapsSchemaError(
      "goal verification boundary caps contract is not a YAML mapping",
    )
  }

  private fun requireKnownKeysOnly(root: Map<*, *>) {
    val unknown = root.keys.map(Any?::toString).filterNot { key -> key in KNOWN_KEYS }.sorted()
    if (unknown.isNotEmpty()) {
      throw InvalidGoalVerificationBoundaryCapsSchemaError(
        "goal verification boundary caps contract declares unknown keys: ${unknown.joinToString(", ")}",
      )
    }
  }

  private fun requireSupportedVersion(version: Any?) {
    if (version != CONTRACT_VERSION) {
      throw InvalidGoalVerificationBoundaryCapsSchemaError(
        "goal verification boundary caps contract_version '$version' is unsupported; expected '$CONTRACT_VERSION'",
      )
    }
  }

  private fun requiredPositiveInt(root: Map<*, *>, key: String): Int =
    root[key].packagedPositiveInt("goal verification boundary caps $key") { message ->
      throw InvalidGoalVerificationBoundaryCapsSchemaError(message)
    }

  private fun requiredPositiveLong(root: Map<*, *>, key: String): Long =
    root[key].packagedPositiveLong("goal verification boundary caps $key") { message ->
      throw InvalidGoalVerificationBoundaryCapsSchemaError(message)
    }
}
