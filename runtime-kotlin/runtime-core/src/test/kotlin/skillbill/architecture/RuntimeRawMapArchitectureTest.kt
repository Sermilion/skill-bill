package skillbill.architecture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeRawMapArchitectureTest {

  private val rawMapOpenBoundaryAllowlistMaxBaseline = 0
  private val rawMapOpenBoundaryEntriesRetiredBySubtask6 = 79
  private val retiredGoalRunnerRawMapPrefixes = listOf(
    "skillbill.engine.goalrunner.",
    "skillbill.engine.goalplanning.",
    "skillbill.goalrunner.",
    "skillbill.ports.goalrunner.",
    "skillbill.workflow.goal.",
  )
  private val rawMapOpenBoundaryAllowlistPreSubtask5Count = 196
  private val rawMapOpenBoundaryEntriesRetiredBySubtask5 = 117

  @Test
  fun `architecture prose does not carry raw-map FQN inventories`() {
    val architecture = Files.readString(runtimeArchitectureRoot.resolve("ARCHITECTURE.md"))
    val rawMapRule = architecture.substringAfter("**Raw Map Boundary Rule")
      .substringBefore("\n12. `java.nio.file.Path`")
    val inventory = architecture.substringAfter("## SKILL-52.2 — Runtime boundary closure inventory")
      .substringBefore("\n# Wire vocabulary")
    assertFalse(rawMapRule.contains("<!-- open-boundary-allowlist:start -->"))
    assertFalse(rawMapRule.contains("<!-- open-boundary-allowlist:end -->"))
    assertFalse(Regex("""(?m)^\s*-\s+`skillbill\.""").containsMatchIn(rawMapRule))
    assertFalse(Regex("""(?m)^\s*-\s+`skillbill\.""").containsMatchIn(inventory))
    assertFalse(inventory.contains("<!-- skill-52-2-inventory:start -->"))
    assertFalse(inventory.contains("<!-- skill-52-2-inventory:end -->"))
  }

  @Test
  fun `allow-list sync script writes canonical sources without architecture output`() {
    val script = Files.readString(runtimeArchitectureRoot.resolve("scripts/sync_raw_map_allowlist.py"))
    assertFalse(script.contains("ARCHITECTURE.md"))
    assertFalse(script.contains("write_architecture_allowlist"))
    assertContains(script, "INVENTORY =")
    assertContains(script, "write_inventory(merged)")
  }

  @Test
  fun `runtime architecture forbids raw map shapes outside the open-boundary allowlist`() {
    val boundaryFiles = sourceFiles().filter { file ->
      file.relativePath.startsWith("runtime-application/src/main/kotlin/") ||
        file.relativePath.startsWith("runtime-domain/src/main/kotlin/") ||
        file.relativePath.startsWith("runtime-ports/src/main/kotlin/")
    }
    val violations = boundaryFiles.flatMap { file ->
      findRawMapViolations(file)
    }
    assertTrue(
      violations.isEmpty(),
      "Public application/domain/port declarations must not use raw Map<String, Any?> " +
        "shapes outside the open-boundary allow-list. Either annotate the declaration with " +
        "@OpenBoundaryMap or add it to RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST in " +
        "RuntimeArchitectureTestSupport.kt.\nViolations:\n" + violations.joinToString(separator = "\n"),
    )
  }

  @Test
  fun `open-boundary allow-list documents required exceptions`() {
    val allowListEntries = RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST
    assertTrue(
      allowListEntries.size <= rawMapOpenBoundaryAllowlistMaxBaseline,
      "RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST must not exceed the subtask-6 baseline.",
    )
    val architecture = Files.readString(runtimeArchitectureRoot.resolve("ARCHITECTURE.md"))
    assertContains(architecture, "RuntimeArchitectureTestSupport.kt")
    assertContains(architecture, "RAW_MAP_OPEN_BOUNDARY_ALLOWLIST")
    assertContains(architecture, "legacy raw-map")
    assertContains(architecture, "grandfathers")
  }

  @Test
  fun `goal runner raw-map allow-list entries remain retired`() {
    val remaining = RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST.filter { entry ->
      retiredGoalRunnerRawMapPrefixes.any { prefix -> entry.startsWith(prefix) }
    }
    assertTrue(
      remaining.isEmpty(),
      "Retired goal-runner raw-map declarations must not return to the open-boundary allow-list: $remaining",
    )
  }

  @Test
  fun `every OpenBoundaryMap annotated declaration is documented in the architecture allow-list`() {
    val boundaryFiles = sourceFiles().filter { file ->
      file.relativePath.startsWith("runtime-application/src/main/kotlin/") ||
        file.relativePath.startsWith("runtime-domain/src/main/kotlin/") ||
        file.relativePath.startsWith("runtime-ports/src/main/kotlin/")
    }
    val annotated = boundaryFiles.flatMap(::findAnnotatedOpenBoundaryDeclarations)
    val allowListEntries = RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST.toSet()
    val undocumented = annotated.filterNot { fqn -> fqn in allowListEntries }
    assertTrue(
      undocumented.isEmpty(),
      "Every @OpenBoundaryMap-annotated public declaration must appear by FQN in " +
        "RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST so the annotation cannot " +
        "act as a silent escape valve.\nUndocumented: $undocumented",
    )
  }

  @Test
  fun `RAW_MAP_OPEN_BOUNDARY_ALLOWLIST size does not exceed shrink baseline`() {
    val size = RuntimeArchitectureScanConstants.RAW_MAP_OPEN_BOUNDARY_ALLOWLIST.size
    val requiredMaximum =
      rawMapOpenBoundaryAllowlistPreSubtask5Count -
        rawMapOpenBoundaryEntriesRetiredBySubtask5 -
        rawMapOpenBoundaryEntriesRetiredBySubtask6
    assertTrue(
      size <= rawMapOpenBoundaryAllowlistMaxBaseline,
      "RAW_MAP_OPEN_BOUNDARY_ALLOWLIST has $size entries (baseline $rawMapOpenBoundaryAllowlistMaxBaseline). " +
        "SKILL-52.5 subtasks 2–6 must shrink the allow-list; do not add FQNs without lowering the count elsewhere.",
    )
    assertTrue(
      size <= requiredMaximum,
      "RAW_MAP_OPEN_BOUNDARY_ALLOWLIST has $size entries; subtask 5 requires at most $requiredMaximum " +
        "after retiring $rawMapOpenBoundaryEntriesRetiredBySubtask5 entries from the " +
        "$rawMapOpenBoundaryAllowlistPreSubtask5Count-entry baseline.",
    )
  }

  @Test
  fun `SKILL-52_2 inventory classifies every public raw-map declaration exactly once`() {
    val inventory = parseSkill522Inventory(loadSkill522InventoryDocument())
    assertInventoryCategoriesKnown(inventory)
    assertInventoryMatchesAllowList(inventory)
    assertInventoryHasNoDuplicateFqns(inventory)
    assertAnnotatedDeclarationsAreOpenExtension(inventory)
    assertSubtaskIdsPresentForGatedCategories(inventory)
  }

  @Test
  fun `SKILL-52_2 inventory parser fires on synthetic fixture`() {
    val fixture =
      """
      <!-- skill-52-2-inventory:start -->

      ### must_type_now

      - `skillbill.fake.MustTypeOne` [subtask 3] — rationale.
      - `skillbill.fake.MustTypeTwo`
        [subtask 5] — wrapped-line rationale.

      ### open_extension (@OpenBoundaryMap)

      - `skillbill.fake.OpenExtensionOne`
      - `skillbill.fake.OpenExtensionTwo`

      ### private_serializer

      _None — placeholder._

      ### postponed_with_reason

      - `skillbill.fake.PostponedOne` [subtask 4] — reason.

      <!-- skill-52-2-inventory:end -->
      """.trimIndent()
    val parsed = parseSkill522Inventory(fixture)
    assertEquals(
      setOf(
        "skillbill.fake.MustTypeOne" to "must_type_now",
        "skillbill.fake.MustTypeTwo" to "must_type_now",
        "skillbill.fake.OpenExtensionOne" to "open_extension",
        "skillbill.fake.OpenExtensionTwo" to "open_extension",
        "skillbill.fake.PostponedOne" to "postponed_with_reason",
      ),
      parsed.entries.map { it.fqn to it.category }.toSet(),
    )
    val subtaskById = parsed.entries.associate { it.fqn to it.subtaskId }
    assertEquals(3, subtaskById["skillbill.fake.MustTypeOne"])
    assertEquals(5, subtaskById["skillbill.fake.MustTypeTwo"])
    assertEquals(4, subtaskById["skillbill.fake.PostponedOne"])
    assertEquals(null, subtaskById["skillbill.fake.OpenExtensionOne"])
  }

  @Test
  fun `raw map violation scanner fires on known violation fixtures`() {
    val fixture = SourceFile(
      relativePath = "test-fixture/Fake.kt",
      packageName = "skillbill.application",
      imports = emptyList(),
      source = rawMapViolationFixtureSource(),
    )
    val violations = findRawMapViolations(fixture)
    val violatingNames = violations.map { it.substringAfter("public `").substringBefore('`') }
    assertEquals(
      expectedRawMapViolationFixtureNames(),
      violatingNames.sorted(),
    )
  }
}
