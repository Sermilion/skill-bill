package skillbill.architecture

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationPackageAcyclicityArchitectureTest {
  @Test
  fun `application package cycles match the recorded baseline`() {
    val violations = ArchitectureScanSupport.packageCycleViolations(
      baselineCycles = baselineCycles("application-package-cycle-baseline.txt"),
      scanRoot = PrincipleEnforcementInventory.RUNTIME_APPLICATION_MAIN,
      packagePrefix = PrincipleEnforcementInventory.APPLICATION_PACKAGE_PREFIX,
    )
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }

  @Test
  fun `runtime-cli package cycles equal the recorded census`() {
    val current = ArchitectureScanSupport.packageCycles(
      scanRoot = PrincipleEnforcementInventory.RUNTIME_CLI_MAIN,
      packagePrefix = PrincipleEnforcementInventory.CLI_PACKAGE_PREFIX,
    )
    assertEquals(
      baselineCycles("runtime-cli-package-cycle-baseline.txt"),
      current,
      "Re-record runtime-cli-package-cycle-baseline.txt with RECORD_ARCHITECTURE_BASELINES=1.",
    )
  }

  @Test
  fun `runtime-contracts package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-contracts")
  }

  @Test
  fun `runtime-core package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-core")
  }

  @Test
  fun `runtime-domain package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-domain")
  }

  @Test
  fun `runtime-infra host package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-infra:host")
  }

  @Test
  fun `runtime-infra contracts package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-infra:contracts")
  }

  @Test
  fun `runtime-infra skills package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-infra:skills")
  }

  @Test
  fun `runtime-infra launcher package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-infra:launcher")
  }

  @Test
  fun `runtime-infra workflow package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-infra:workflow")
  }

  @Test
  fun `runtime-infra http package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-infra:http")
  }

  @Test
  fun `runtime-infra sqlite package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-infra:sqlite")
  }

  @Test
  fun `runtime-mcp package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-mcp")
  }

  @Test
  fun `runtime-ports package cycles equal the recorded census`() {
    assertPackageCyclesMatchBaseline("runtime-ports")
  }

  @Test
  fun `package cycle scanner fires on synthetic cycle absent from baseline`() {
    val violations = ArchitectureScanSupport.packageCycleViolationsForEdges(
      edges = mapOf(
        "alpha" to setOf("beta"),
        "beta" to setOf("alpha"),
      ),
      baselineCycles = emptySet(),
    )
    assertEquals(
      listOf("New package cycle not in baseline: alpha <-> beta"),
      violations,
    )
    assertTrue(violations.single().contains("alpha <-> beta"))
  }

  @Test
  fun `engine model subareas have an empty parent import baseline`() {
    val forbiddenEdges = listOf(
      "skillbill.engine.featuretask.model" to "skillbill.engine.featuretask",
      "skillbill.engine.goalrunner.model" to "skillbill.engine.goalrunner",
      "skillbill.engine.goalrunner.planning.model" to "skillbill.engine.goalrunner.planning",
    )
    val engineRoot = ArchitectureScanSupport.runtimeRoot.resolve(
      "runtime-kotlin/runtime-engine/src/main/kotlin",
    )
    val violations = ArchitectureScanSupport.kotlinFilesUnder(engineRoot).flatMap { sourceFile ->
      val source = sourceFile.readText()
      val declaredPackage = ArchitectureScanSupport.declaredPackage(source)
      if (declaredPackage == null) {
        emptyList()
      } else {
        forbiddenEdges.flatMap { (modelPackage, parentPackage) ->
          if (declaredPackage != modelPackage) {
            emptyList()
          } else {
            ArchitectureScanSupport.declaredImports(source)
              .filter { imported -> imported == parentPackage || imported.startsWith("$parentPackage.") }
              .map { imported -> "${engineRoot.relativize(sourceFile)}: $modelPackage -> $imported" }
          }
        }
      }
    }
    assertEquals(emptyList(), violations)
  }

  @Test
  fun `engine subareas have an empty package cycle baseline`() {
    val engineRoot = "runtime-kotlin/runtime-engine/src/main/kotlin"
    val subareas = listOf(
      "skillbill.engine.featuretask",
      "skillbill.engine.goalrunner",
      "skillbill.engine.goalrunner.planning",
    )
    val violations = subareas.flatMap { packagePrefix ->
      ArchitectureScanSupport.packageCycleViolations(
        baselineCycles = emptySet(),
        scanRoot = engineRoot,
        packagePrefix = packagePrefix,
      ).map { violation -> "$packagePrefix: $violation" }
    }
    assertEquals(emptyList(), violations)
  }

  @Test
  fun `engine model packages contain no injected data classes or goal runner dependency bag`() {
    val engineRoot = ArchitectureScanSupport.runtimeRoot.resolve(
      "runtime-kotlin/runtime-engine/src/main/kotlin",
    )
    val violations = ArchitectureScanSupport.kotlinFilesUnder(engineRoot).flatMap { sourceFile ->
      val source = sourceFile.readText()
      val packageName = ArchitectureScanSupport.declaredPackage(source).orEmpty()
      buildList {
        if (packageName.endsWith(".model") &&
          Regex("""@Inject\s+data\s+class""").containsMatchIn(source)
        ) {
          add("${engineRoot.relativize(sourceFile)} declares an injected model data class")
        }
        if (Regex("""\b(?:class|data class|interface)\s+GoalRunnerDeps\b""").containsMatchIn(source)) {
          add("${engineRoot.relativize(sourceFile)} declares GoalRunnerDeps")
        }
      }
    }
    assertEquals(emptyList(), violations)
  }

  @Test
  fun `goal runner exposes direct dependencies below the constructor threshold`() {
    val source = ArchitectureScanSupport.runtimeRoot.resolve(
      "runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/goalrunner/GoalRunner.kt",
    ).readText()
    val constructor = source.substringAfter("class GoalRunner(").substringBefore(") {")
    assertEquals(4, Regex("""private val \w+:""").findAll(constructor).count())
    assertTrue(!Regex("""\bfun\s+get\s*\(""").containsMatchIn(source))
    assertTrue(!Regex("""\bGoalRunnerDeps\b""").containsMatchIn(source))
  }

  private fun assertPackageCyclesMatchBaseline(moduleName: String) {
    val scanCase = PrincipleEnforcementInventory.moduleArchitectureScanCases
      .single { scanCase -> scanCase.moduleName == moduleName }
    val current = ArchitectureScanSupport.packageCycles(scanCase.mainScanRoot, scanCase.packagePrefix)
    assertEquals(
      baselineCycles(scanCase.packageCycleBaseline),
      current,
      "Re-record ${scanCase.packageCycleBaseline} with RECORD_ARCHITECTURE_BASELINES=1.",
    )
  }

  private fun baselineCycles(name: String): Set<ArchitectureScanSupport.PackageCycle> =
    ArchitectureScanSupport.parsePackageCycleBaseline(ArchitectureBaselineSupport.readBaseline(name))
}
