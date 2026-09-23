package skillbill.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeCompositionGuardArchitectureTest {
  private val diRoot = "runtime-kotlin/runtime-core/src/main/kotlin/skillbill/di"
  private val boundClasses = ArchitectureScanSupport.boundComponentConcreteClassNames(diRoot)
  private val scanRoots = PrincipleEnforcementInventory.moduleArchitectureScanCases.map { it.mainScanRoot }

  @Test
  fun `no main-source site outside skillbill di constructs a component-bound class`() {
    val violations =
      ArchitectureScanSupport.directComponentConstructionViolations(
        boundClassNames = boundClasses,
        scanRoots = scanRoots,
        compositionDiRoot = diRoot,
        sanctionedEntrypoints = PrincipleEnforcementInventory.sanctionedCompositionEntrypoints,
      )
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }

  @Test
  fun `composition guard fires on synthetic bound-class construction outside skillbill di`() {
    val boundClasses =
      ArchitectureScanSupport.boundComponentConcreteClassNamesInSource(
        """
        import me.tatarka.inject.annotations.Provides
        @Provides
        fun bindStore(store: FileTelemetryConfigStore): FileTelemetryConfigStore = store
        @Provides
        fun bindService(service: TelemetryLevelMutationService): TelemetryLevelMutationService = service
        """.trimIndent(),
      )
    val violations =
      ArchitectureScanSupport.directComponentConstructionViolationsForSource(
        boundClassNames = boundClasses,
        relativePath = "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt",
        source =
          """
          package skillbill.example
          import skillbill.infrastructure.host.FileTelemetryConfigStore
          class Example {
            fun leak() {
              FileTelemetryConfigStore(context)
              TelemetryLevelMutationService(database, settings, configStore)
            }
          }
          """.trimIndent(),
      )
    assertEquals(
      listOf(
        "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt constructs " +
          "FileTelemetryConfigStore outside skillbill.di",
        "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt constructs " +
          "TelemetryLevelMutationService outside skillbill.di",
      ),
      violations,
    )
  }

  @Test
  fun `composition guard ignores sanctioned second entrypoints`() {
    val violations =
      ArchitectureScanSupport.directComponentConstructionViolations(
        boundClassNames = setOf("FileSystemScaffoldRepoValidation", "FileSystemScaffoldSourceLoader"),
        scanRoots =
          listOf(
            "${RuntimeModuleCatalog.runtimeKotlinModuleDirectory("runtime-infra:skills")}/src/main/kotlin/" +
              "skillbill/infrastructure/skills/scaffold/runtime/service/standalone",
          ),
        compositionDiRoot = diRoot,
        sanctionedEntrypoints = PrincipleEnforcementInventory.sanctionedCompositionEntrypoints,
      )
    assertEquals(emptyList(), violations)
  }

  @Test
  fun `bound-class census is non-empty`() {
    assertTrue(boundClasses.isNotEmpty(), "skillbill.di must declare at least one concrete bound class.")
    assertTrue(
      setOf("EnvironmentContext", "OptionalCallbacks", "RuntimeContext", "WorkflowOpsContext")
        .none(boundClasses::contains),
      "Graph input/output values must not be treated as concrete composition bindings: $boundClasses",
    )
  }

  @Test
  fun `bound-class census includes renamed provider parameter types`() {
    val source =
      """
      package skillbill.di
      import me.tatarka.inject.annotations.Provides
      internal interface RuntimeExampleProvides {
        @Provides @JvmSynthetic
        fun bind(implementation: ExampleAdapter): ExamplePort = implementation
      }
      """.trimIndent()
    assertEquals(
      setOf("ExampleAdapter"),
      ArchitectureScanSupport.boundComponentConcreteClassNamesInSource(source),
    )
  }

  @Test
  fun `bound-class census includes explicit Provides constructions`() {
    val source =
      """
      package skillbill.di
      import me.tatarka.inject.annotations.Provides
      internal interface RuntimeWorkflowProvides {
        @Provides @JvmSynthetic
        fun gitWorkflowGitOperations(): GitWorkflowGitOperations = GitWorkflowGitOperations()
      }
      """.trimIndent()
    assertEquals(
      setOf("GitWorkflowGitOperations"),
      ArchitectureScanSupport.boundComponentConcreteClassNamesInSource(source),
    )
  }

  @Test
  fun `composition guard rejects alias import construction`() {
    val boundClasses =
      ArchitectureScanSupport.boundComponentConcreteClassNamesInSource(
        """
        import me.tatarka.inject.annotations.Provides
        @Provides
        fun bind(store: FileTelemetryConfigStore): FileTelemetryConfigStore = store
        """.trimIndent(),
      )
    val violations =
      ArchitectureScanSupport.directComponentConstructionViolationsForSource(
        boundClassNames = boundClasses,
        relativePath = "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt",
        source =
          """
          package skillbill.example
          import skillbill.infrastructure.host.FileTelemetryConfigStore as StoreAlias
          class Example {
            fun leak() {
              StoreAlias(context)
            }
          }
          """.trimIndent(),
      )
    assertEquals(
      listOf(
        "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt constructs " +
          "FileTelemetryConfigStore outside skillbill.di",
      ),
      violations,
    )
  }

  @Test
  fun `composition guard preserves provider binding identity through aliases`() {
    val boundClasses =
      ArchitectureScanSupport.boundComponentConcreteClassNamesInSource(
        """
        import me.tatarka.inject.annotations.Provides
        import skillbill.infrastructure.host.FileTelemetryConfigStore as StoreAlias
        @Provides
        fun bind(store: StoreAlias): TelemetryConfigStore = store
        """.trimIndent(),
      )
    val violations =
      ArchitectureScanSupport.directComponentConstructionViolationsForSource(
        boundClassNames = boundClasses,
        relativePath = "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt",
        source =
          """
          package skillbill.example
          import skillbill.infrastructure.host.FileTelemetryConfigStore
          class Example {
            fun leak() {
              FileTelemetryConfigStore(context)
            }
          }
          """.trimIndent(),
      )
    assertEquals(
      setOf("FileTelemetryConfigStore"),
      boundClasses,
    )
    assertEquals(
      listOf(
        "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt constructs " +
          "FileTelemetryConfigStore outside skillbill.di",
      ),
      violations,
    )
  }

  @Test
  fun `composition guard ignores same-named functions strings and comments`() {
    val violations =
      ArchitectureScanSupport.directComponentConstructionViolationsForSource(
        boundClassNames = setOf("FileTelemetryConfigStore"),
        relativePath = "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt",
        source =
          """
          package skillbill.example
          fun FileTelemetryConfigStore() = Unit
          val message = "FileTelemetryConfigStore(context)"
          // FileTelemetryConfigStore(ignored)
          """.trimIndent(),
      )
    assertEquals(emptyList(), violations)
  }

  @Test
  fun `composition guard ignores multiline comments and constructor-like string delimiters`() {
    val violations =
      ArchitectureScanSupport.directComponentConstructionViolationsForSource(
        boundClassNames = setOf("FileTelemetryConfigStore"),
        relativePath = "runtime-kotlin/runtime-example/src/main/kotlin/skillbill/example/Example.kt",
        source =
          """
          package skillbill.example
          /*
            FileTelemetryConfigStore(ignored)
          */
          val message = "A // comment and /* block */ FileTelemetryConfigStore(context)"
          """.trimIndent(),
      )
    assertEquals(emptyList(), violations)
  }
}
