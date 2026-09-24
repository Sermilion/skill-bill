package skillbill.architecture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeApplicationAmbientClockArchitectureTest {
  @Test
  fun `every declared module matches its ambient clock baseline`() {
    val drift = ArchitectureScanSupport.ambientClockDrift()
    assertEquals(emptyList(), drift, drift.joinToString("\n"))
  }

  @Test
  fun `ambient clock rule reports a violation placed in runtime-engine`() {
    val root = Files.createTempDirectory("skillbill-ambient-clock-rejection")
    seedModuleScanTreeWithEngineViolation(
      root,
      """
      package skillbill.engine

      import java.time.Clock
      import me.tatarka.inject.annotations.Inject

      @Inject
      class SyntheticEngineService(private val clock: Clock = Clock.systemUTC())
      """.trimIndent(),
    )
    val drift = ArchitectureScanSupport.ambientClockDrift(scanRoot = root, readBaseline = { "" })
    assertEquals(
      listOf(
        "runtime-engine: $SYNTHETIC_ENGINE_VIOLATION_PATH:Clock.systemUTC():1 " +
          "is not listed in runtime-engine-ambient-clock-baseline.txt.",
      ),
      drift,
    )
  }

  @Test
  fun `ambient clock rows key on call and count, not line number`() {
    val onLineFive =
      """
      package skillbill.example

      import java.time.Instant

      fun nowMarker() = Instant.now()
      """.trimIndent()
    val movedToLineNine =
      """
      package skillbill.example

      import java.time.Instant

      fun unrelated() = 1

      fun alsoUnrelated() = 2

      fun nowMarker() = Instant.now()
      """.trimIndent()
    val twiceInOneFile = "$onLineFive\n\nfun secondMarker() = Instant.now()\n"
    assertEquals(listOf("$EXAMPLE_PATH:Instant.now():1"), encode(onLineFive))
    assertEquals(encode(onLineFive), encode(movedToLineNine))
    assertEquals(listOf("$EXAMPLE_PATH:Instant.now():2"), encode(twiceInOneFile))
  }

  @Test
  fun `ambient clock scanner fires on unlisted Instant now site`() {
    assertScannerReports("fun nowMarker() = Instant.now()", "Instant.now()")
  }

  @Test
  fun `ambient clock scanner fires on unlisted OffsetDateTime now site`() {
    assertScannerReports("fun nowMarker() = OffsetDateTime.now(ZoneOffset.UTC)", "OffsetDateTime.now()")
  }

  @Test
  fun `ambient clock scanner fires on unlisted ZonedDateTime now site`() {
    assertScannerReports("fun nowMarker() = ZonedDateTime.now()", "ZonedDateTime.now()")
  }

  @Test
  fun `ambient clock scanner fires on unlisted JvmSystemClock instant site`() {
    assertScannerReports("fun nowMarker() = JvmSystemClock.instant()", "JvmSystemClock.instant()")
  }

  @Test
  fun `ambient clock scanner fires on unlisted LocalDate now site`() {
    assertScannerReports("fun todayMarker() = LocalDate.now()", "LocalDate.now()")
  }

  private fun assertScannerReports(
    declaration: String,
    expectedCall: String,
  ) {
    val violations =
      ArchitectureScanSupport.ambientClockViolationsInSource(
        relativePath = EXAMPLE_PATH,
        source = "package skillbill.example\n\n$declaration\n",
        baseline = emptySet(),
      )
    assertEquals(
      listOf("$EXAMPLE_PATH:$expectedCall:1 is not listed in the ambient-clock baseline."),
      violations,
    )
  }

  private fun encode(source: String): List<String> =
    ArchitectureScanSupport.encodeAmbientClockSitesInSource(EXAMPLE_PATH, source)

  private companion object {
    const val EXAMPLE_PATH = "runtime-kotlin/runtime-example/src/main/kotlin/Example.kt"
  }
}
