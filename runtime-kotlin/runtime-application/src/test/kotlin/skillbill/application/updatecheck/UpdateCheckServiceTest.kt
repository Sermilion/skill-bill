package skillbill.application.updatecheck

import skillbill.application.system.SystemService
import skillbill.application.updatecheck.model.RECOMMENDED_INSTALL_COMMAND
import skillbill.application.updatecheck.model.UpdateCheckStatus
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.telemetry.TelemetrySettingsProvider
import skillbill.ports.telemetry.model.RemoteTransportResponse
import skillbill.telemetry.model.TelemetrySettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateCheckServiceTest {
  private val installedVersion = "0.3.0-SNAPSHOT"

  @Test
  fun `maps update available up to date and ahead of release`() {
    val update = service(responseBody = releases("v0.4.0")).check(includePrereleases = false)
    assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, update.status)
    assertEquals(installedVersion, update.installedVersion)
    assertEquals("v0.4.0", update.latestVersion)
    assertEquals(RECOMMENDED_INSTALL_COMMAND, update.recommendedInstallCommand)

    val upToDate = service(responseBody = releases("v0.3.0-SNAPSHOT")).check(includePrereleases = true)
    assertEquals(UpdateCheckStatus.UP_TO_DATE, upToDate.status)
    assertNull(upToDate.recommendedInstallCommand)

    val ahead = service(responseBody = releases("v0.2.0")).check(includePrereleases = false)
    assertEquals(UpdateCheckStatus.AHEAD_OF_RELEASE, ahead.status)
  }

  @Test
  fun `same-base snapshot is behind the matching release`() {
    val update = service(responseBody = releases("v0.3.0")).check(includePrereleases = false)
    assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, update.status)
    assertEquals("v0.3.0", update.latestVersion)
    assertEquals(RECOMMENDED_INSTALL_COMMAND, update.recommendedInstallCommand)
  }

  @Test
  fun `selects stable releases by default and prereleases when requested`() {
    val body = releases("v0.4.0-rc.1", "v0.2.0")

    val stable = service(responseBody = body).check(includePrereleases = false)
    assertEquals(UpdateCheckStatus.AHEAD_OF_RELEASE, stable.status)
    assertEquals("v0.2.0", stable.latestVersion)

    val prerelease = service(responseBody = body).check(includePrereleases = true)
    assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, prerelease.status)
    assertEquals("v0.4.0-rc.1", prerelease.latestVersion)
  }

  @Test
  fun `ignores a newer plugin release and selects the newest runtime semver`() {
    val body = "[${releaseEntry("plugin-v9.9.9", prerelease = false)},${releaseEntry("v0.4.0")}]"

    val result = service(responseBody = body).check(includePrereleases = false)

    assertEquals("v0.4.0", result.latestVersion)
    assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, result.status)
    assertNull(result.reason)
  }

  @Test
  fun `an unversioned build is never told to update`() {
    val result = UpdateCheckService(
      systemService = SystemService(
        TestDatabaseSessionFactory(),
        TestTelemetrySettingsProvider,
        NoopRuntimeDiagnostics,
        versionValue = "0.0.0-SNAPSHOT",
      ),
      requester = RemoteTransportPort { _, _, _, _ -> error("release list must not be consulted") },
    ).check(includePrereleases = false)

    assertEquals(UpdateCheckStatus.UNKNOWN, result.status)
    assertEquals("0.0.0-SNAPSHOT", result.installedVersion)
    assertNull(result.recommendedInstallCommand)
  }

  @Test
  fun `missing and malformed installed versions stay unknown without release lookup`() {
    val missing = service(versionValue = "", responseBody = releases("v9.9.9")).check(false)
    assertEquals(UpdateCheckStatus.UNKNOWN, missing.status)
    assertEquals("missing local version metadata", missing.reason)

    val malformed = service(versionValue = "not-semver", responseBody = releases("v9.9.9")).check(false)
    assertEquals(UpdateCheckStatus.UNKNOWN, malformed.status)
    assertEquals("local version is not semver", malformed.reason)
  }

  @Test
  fun `whitespace formatted empty release arrays are not treated as malformed`() {
    val result = service(responseBody = "[ ]").check(includePrereleases = false)
    assertEquals(UpdateCheckStatus.UNKNOWN, result.status)
    assertEquals("no GitHub releases returned", result.reason)
  }

  @Test
  fun `maps soft failures to unknown`() {
    assertEquals(UpdateCheckStatus.UNKNOWN, service(responseBody = "not-json").check(false).status)
    assertEquals(UpdateCheckStatus.UNKNOWN, service(responseBody = "[]").check(false).status)
    assertEquals(UpdateCheckStatus.UNKNOWN, service(statusCode = 429, responseBody = "").check(false).status)
    assertEquals(UpdateCheckStatus.UNKNOWN, service(responseBody = releases("nonsense")).check(false).status)
  }

  @Test
  fun `overlapping checks keep independent failure reasons and valid results`() {
    val validBody = releases("v0.4.0")
    val malformedBody = "[{\"tag_name\":\"v0.4.0\"}]"
    val callCount = AtomicInteger(0)
    val firstEntered = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)
    val shared = UpdateCheckService(
      systemService = SystemService(
        TestDatabaseSessionFactory(),
        TestTelemetrySettingsProvider,
        NoopRuntimeDiagnostics,
        versionValue = installedVersion,
      ),
      requester = RemoteTransportPort { _, _, _, _ ->
        when (callCount.incrementAndGet()) {
          1 -> {
            firstEntered.countDown()
            releaseFirst.await()
            RemoteTransportResponse(statusCode = 200, body = malformedBody)
          }
          else -> {
            releaseFirst.countDown()
            RemoteTransportResponse(statusCode = 200, body = validBody)
          }
        }
      },
    )
    var malformedReason: String? = null
    var validStatus: UpdateCheckStatus? = null
    val malformedThread = Thread {
      malformedReason = shared.check(includePrereleases = false).reason
    }
    val validThread = Thread {
      firstEntered.await()
      validStatus = shared.check(includePrereleases = false).status
    }
    malformedThread.start()
    validThread.start()
    malformedThread.join()
    validThread.join()
    assertEquals("malformed release entry", malformedReason)
    assertEquals(UpdateCheckStatus.UPDATE_AVAILABLE, validStatus)
  }

  private fun service(
    statusCode: Int = 200,
    responseBody: String,
    versionValue: String = installedVersion,
  ): UpdateCheckService = UpdateCheckService(
    systemService = SystemService(
      TestDatabaseSessionFactory(),
      TestTelemetrySettingsProvider,
      NoopRuntimeDiagnostics,
      versionValue = versionValue,
    ),
    requester = RemoteTransportPort { method, url, _, headers ->
      assertEquals("GET", method)
      assertEquals("https://api.github.com/repos/oila-gmbh/skill-bill/releases", url)
      assertEquals("skill-bill-update-check", headers["User-Agent"])
      RemoteTransportResponse(statusCode = statusCode, body = responseBody)
    },
  )
}

private fun releases(vararg tags: String): String =
  tags.joinToString(prefix = "[", postfix = "]") { tag -> releaseEntry(tag) }

private fun releaseEntry(tag: String, prerelease: Boolean = tag.contains("-")): String = """
      {
        "tag_name":"$tag",
        "prerelease":$prerelease,
        "draft":false,
        "html_url":"https://github.com/oila-gmbh/skill-bill/releases/tag/$tag"
      }
""".trimIndent()

private class TestDatabaseSessionFactory : DatabaseSessionFactory {
  private val dbPath = Files.createTempDirectory("skillbill-update-check-db").resolve("metrics.db")

  override fun resolveDbPath(): Path = dbPath
  override fun databaseExists(): Boolean = Files.exists(resolveDbPath())
  override fun <T> read(block: (UnitOfWork) -> T): T = error("unused")
  override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T = transaction(block)

  override fun <T> transaction(block: (UnitOfWork) -> T): T = error("unused")
}

private object TestTelemetrySettingsProvider : TelemetrySettingsProvider {
  override fun load(materialize: Boolean): TelemetrySettings = error("unused")
}
