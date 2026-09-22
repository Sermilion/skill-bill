package skillbill.contracts.goalplanning

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.packaged.PackagedYamlMappingFailure
import skillbill.contracts.packaged.loadPackagedYamlRootMapping
import skillbill.contracts.packaged.requireUniqueStringItems
import skillbill.error.shellcontent.InvalidGoalPlanningDiscoveryExclusionsSchemaError

object GoalPlanningDiscoveryExclusions {
  const val CONTRACT_VERSION = "0.3"
  const val RESOURCE_PATH = "skillbill/infrastructure/contracts/goal-planning-discovery-exclusions.yaml"
  const val CONTRACT_FILE = "orchestration/contracts/goal-planning-discovery-exclusions.yaml"
  const val SCHEMA_FILE = "orchestration/contracts/goal-planning-discovery-exclusions-schema.yaml"

  private val KNOWN_KEYS = setOf("contract_version", "excluded_roots", "excluded_directory_names")

  private val contract: Contract by lazy { parse(readContract()) }

  val excludedRoots: List<String> get() = contract.roots

  val excludedDirectoryNames: List<String> get() = contract.directoryNames

  fun isExcluded(relativePath: String): Boolean {
    val normalized = normalize(relativePath) ?: return true
    if (normalized.isEmpty()) return false
    if (excludedRoots.any { root -> "$normalized/".startsWith(root) }) return true
    val names = excludedDirectoryNames
    return normalized.split("/").any { segment -> segment in names }
  }

  private fun normalize(relativePath: String): String? {
    val segments = mutableListOf<String>()
    for (segment in relativePath.replace('\\', '/').split("/")) {
      when (segment) {
        "", "." -> Unit
        ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
        else -> segments.add(segment)
      }
    }
    return segments.joinToString("/")
  }

  internal data class Contract(val roots: List<String>, val directoryNames: List<String>)

  private fun readContract(): String =
    javaClass.classLoader.getResourceAsStream(RESOURCE_PATH)?.use { stream -> stream.readBytes().decodeToString() }
      ?: throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
        "goal planning discovery exclusion contract is missing from the classpath at $RESOURCE_PATH",
      )

  internal fun parse(document: String): Contract {
    val root = loadRootMapping(document)
    requireKnownKeysOnly(root)
    requireSupportedVersion(root[SharedPayloadKeys.CONTRACT_VERSION])
    return Contract(
      roots = requiredStringList(root, "excluded_roots").onEach(::requireNormalizedRoot),
      directoryNames = requiredStringList(root, "excluded_directory_names").onEach(::requireBareDirectoryName),
    )
  }

  private fun loadRootMapping(document: String): Map<*, *> =
    try {
      loadPackagedYamlRootMapping(
        document,
        "goal planning discovery exclusion contract is not a YAML mapping",
      )
    } catch (_: PackagedYamlMappingFailure) {
      throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
        "goal planning discovery exclusion contract is not a YAML mapping",
      )
    }

  private fun requireKnownKeysOnly(root: Map<*, *>) {
    val unknown = root.keys.map(Any?::toString).filterNot { key -> key in KNOWN_KEYS }.sorted()
    if (unknown.isNotEmpty()) {
      throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
        "goal planning discovery exclusion contract declares unknown keys: ${unknown.joinToString(", ")}",
      )
    }
  }

  private fun requireSupportedVersion(version: Any?) {
    if (version != CONTRACT_VERSION) {
      throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
        "goal planning discovery exclusion contract_version '$version' is unsupported; expected '$CONTRACT_VERSION'",
      )
    }
  }

  private fun requiredStringList(
    root: Map<*, *>,
    key: String,
  ): List<String> {
    val entries = (root[key] as? List<*>)?.map { entry -> requiredStringListEntry(entry, key) }.orEmpty()
    requireStringListNotEmpty(entries, key)
    requireUniqueStringItems(entries, "goal planning discovery exclusion $key") { message ->
      throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(message)
    }
    return entries
  }

  private fun requiredStringListEntry(
    entry: Any?,
    key: String,
  ): String =
    entry as? String ?: throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
      "goal planning discovery exclusion $key entry '$entry' is not a string",
    )

  private fun requireStringListNotEmpty(
    entries: List<String>,
    key: String,
  ) {
    if (entries.isEmpty()) {
      throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
        "goal planning discovery exclusion contract declares no $key",
      )
    }
  }

  private fun requireBareDirectoryName(name: String) {
    val invalid = name.isBlank() || name in setOf(".", "..") || name.any { char -> char == '/' || char == '\\' }
    if (invalid) {
      throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
        "goal planning discovery excluded_directory_names entry '$name' must be a bare directory name",
      )
    }
  }

  private fun requireNormalizedRoot(root: String) {
    val invalid =
      root.isBlank() ||
        !root.endsWith("/") ||
        root.startsWith("/") ||
        root.startsWith("./") ||
        root.contains("\\") ||
        root.split("/").any { segment -> segment == ".." || segment == "." }
    if (invalid) {
      throw InvalidGoalPlanningDiscoveryExclusionsSchemaError(
        "goal planning discovery exclusion root '$root' must be a normalized repo-relative prefix ending in '/'",
      )
    }
  }
}
