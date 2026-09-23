package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeRawMapArchitectureTest {
  @Test
  fun `architecture prose does not carry raw-map FQN inventories`() {
    val architecture = Files.readString(runtimeArchitectureRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))
    val rawMapRule =
      architecture.substringAfter("**Raw Map Boundary Rule")
        .substringBefore("\n12. `java.nio.file.Path`")
    assertFalse(rawMapRule.contains("<!-- open-boundary-allowlist:start -->"))
    assertFalse(rawMapRule.contains("<!-- open-boundary-allowlist:end -->"))
    assertFalse(Regex("""(?m)^\s*-\s+`skillbill\.""").containsMatchIn(rawMapRule))
    assertFalse(architecture.contains("<!-- skill-52-2-inventory:start -->"))
    assertFalse(architecture.contains("<!-- skill-52-2-inventory:end -->"))
  }

  @Test
  fun `raw-map allow-list machinery is absent`() {
    assertFalse(Files.exists(runtimeArchitectureRoot.resolve("scripts/sync_raw_map_allowlist.py")))
    val retiredConstant = listOf("RAW_MAP", "OPEN_BOUNDARY", "ALLOWLIST").joinToString("_")
    val retiredParserSymbols =
      listOf(
        listOf("parseArchitecture", "AllowList").joinToString(""),
        listOf("parseSkill522", "Inventory").joinToString(""),
        listOf("assertInventoryMatches", "AllowList").joinToString(""),
      )
    val staleReferences =
      Files.walk(runtimeArchitectureRoot).use { paths ->
        paths
          .filter { path ->
            val normalized = path.toString().replace('\\', '/')
            !normalized.contains("/.feature-specs/") && !normalized.contains("/.git/")
          }
          .filter { path ->
            Files.isRegularFile(path) &&
              path.fileName.toString().substringAfterLast('.', "") in setOf("kt", "md", "py", "yaml", "yml", "sh")
          }
          .filter { path ->
            val source = Files.readString(path)
            retiredConstant in source || retiredParserSymbols.any(source::contains)
          }
          .map(runtimeArchitectureRoot::relativize)
          .map(Path::toString)
          .toList()
      }
    assertEquals(emptyList(), staleReferences)
  }

  @Test
  fun `production sources contain no open-boundary annotation references`() {
    val annotation = listOf("@OpenBoundary", "Map").joinToString("")
    val violations =
      declaredMainSourceFiles()
        .filter { file -> annotation in file.source }
        .map(SourceFile::relativePath)
    assertEquals(emptyList(), violations)
  }

  @Test
  fun `runtime architecture forbids public raw map shapes in inner layers`() {
    val violations =
      listOf("runtime-application", "runtime-domain", "runtime-ports")
        .flatMap { moduleName -> rawMapViolationsUnder(moduleMainKotlinRoot(moduleName)) }
    assertTrue(
      violations.isEmpty(),
      "Public application/domain/port declarations must not use raw Map<String, Any?> " +
        "shapes. Contain maps in private or adapter-only serializers, or replace them with typed models.\n" +
        "Violations:\n" + violations.joinToString(separator = "\n"),
    )
  }

  @Test
  fun `inner-layer raw-map scanner rejects synthetic public map in application main source`() {
    val root = Files.createTempDirectory("raw-map-fixture")
    val sourceRoot = root.resolve("runtime-application/src/main/kotlin")
    Files.createDirectories(sourceRoot.resolve("skillbill/application/fixture"))
    val sourceFile = sourceRoot.resolve("skillbill/application/fixture/SyntheticLeak.kt")
    Files.writeString(
      sourceFile,
      """
      package skillbill.application.fixture

      class SyntheticLeak {
        fun payload(): Map<String, Any?> = emptyMap()
      }
      """.trimIndent(),
    )
    val violations = rawMapViolationsUnder(sourceRoot)
    assertEquals(1, violations.size)
    assertTrue(
      violations.single().contains("SyntheticLeak.kt") &&
        violations.single().contains("payload") &&
        violations.single().contains(sourceRoot.toString()),
      "The inner-layer scanner must report the public raw-map declaration it read from the fixture root.",
    )
  }

  @Test
  fun `ports main path disables suffix boundary-carrier exemption for FooMap`() {
    val fixture =
      SourceFile(
        relativePath =
          "${moduleMainKotlinRootRelative("runtime-ports")}/skillbill/ports/fixture/FooMap.kt",
        packageName = "skillbill.ports.fixture",
        imports = emptyList(),
        source =
          """
          package skillbill.ports.fixture

          class FooMap(private val delegate: Map<String, Any?>) : Map<String, Any?> by delegate
          """.trimIndent(),
      )
    val violations = findRawMapViolations(fixture)
    assertEquals(
      listOf(
        "${moduleMainKotlinRootRelative("runtime-ports")}/skillbill/ports/fixture/FooMap.kt:3: " +
          "public `FooMap` exposes raw map shape",
      ),
      violations,
    )
  }

  @Test
  fun `raw map violation scanner fires on known violation fixtures`() {
    val fixture =
      SourceFile(
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
