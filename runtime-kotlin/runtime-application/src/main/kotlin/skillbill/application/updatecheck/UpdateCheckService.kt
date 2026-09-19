package skillbill.application.updatecheck

import me.tatarka.inject.annotations.Inject
import skillbill.application.system.SystemService
import skillbill.application.updatecheck.model.RECOMMENDED_INSTALL_COMMAND
import skillbill.application.updatecheck.model.Semver
import skillbill.application.updatecheck.model.UpdateCheckResult
import skillbill.application.updatecheck.model.UpdateCheckStatus
import skillbill.contracts.JsonCodec
import skillbill.error.ShellContentContractException
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.telemetry.model.RemoteTransportResponse
import java.io.IOException

@Inject
class UpdateCheckService(
  private val systemService: SystemService,
  private val requester: RemoteTransportPort,
) {
  fun check(includePrereleases: Boolean): UpdateCheckResult {
    val installedVersion = systemService.version().version
    val installed = installedVersion.takeIf(String::isNotBlank)?.let(Semver::parse)
    return when {
      installedVersion.isBlank() -> unknown("missing local version metadata")
      installed == null -> unknown("local version is not semver", installedVersion = installedVersion)
      installed.isUnversionedBuild -> unknown(
        "local build carries no version provenance; reinstall with `skill-bill update --release <tag>`",
        installedVersion = installedVersion,
      )
      else -> checkReleases(installedVersion, installed, includePrereleases)
    }
  }

  private fun checkReleases(
    installedVersion: String,
    installed: Semver,
    includePrereleases: Boolean,
  ): UpdateCheckResult {
    val fetch = fetchReleases()
    return fetch.failure ?: fetch.releases?.let { releases ->
      val selection = selectLatestRelease(releases, includePrereleases)
      selection.failure ?: selection.candidate?.let { latest ->
        updateResult(installedVersion, latest, updateStatus(installed, latest.version))
      }
    } ?: unknown("release check did not complete")
  }

  private fun updateResult(
    installedVersion: String,
    latest: ReleaseCandidate,
    status: UpdateCheckStatus,
  ): UpdateCheckResult = UpdateCheckResult(
    status = status,
    installedVersion = installedVersion,
    latestVersion = latest.tagName,
    releaseUrl = latest.url,
    releaseNotes = latest.body,
    recommendedInstallCommand = if (status == UpdateCheckStatus.UPDATE_AVAILABLE) {
      RECOMMENDED_INSTALL_COMMAND
    } else {
      null
    },
  )

  private fun updateStatus(installed: Semver, latest: Semver): UpdateCheckStatus = when {
    installed < latest -> UpdateCheckStatus.UPDATE_AVAILABLE
    installed > latest -> UpdateCheckStatus.AHEAD_OF_RELEASE
    else -> UpdateCheckStatus.UP_TO_DATE
  }

  private fun fetchReleases(): ReleaseFetchResult {
    val response = try {
      requester.execute(
        method = "GET",
        url = RELEASES_URL,
        bodyJson = null,
        headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to USER_AGENT),
      )
    } catch (error: IOException) {
      return ReleaseFetchResult(failure = unknown("network failure: ${errorMessage(error)}"))
    } catch (error: InterruptedException) {
      throw error
    } catch (error: IllegalArgumentException) {
      return ReleaseFetchResult(failure = unknown("network failure: ${errorMessage(error)}"))
    }
    return parseReleasesResponse(response)
  }

  private fun parseReleasesResponse(response: RemoteTransportResponse): ReleaseFetchResult {
    val errorReason = when {
      response.statusCode == HTTP_FORBIDDEN || response.statusCode == HTTP_TOO_MANY_REQUESTS ->
        "GitHub API rate limit or access limit"
      response.statusCode !in HTTP_SUCCESS_RANGE ->
        "GitHub Releases request failed with HTTP ${response.statusCode}"
      else -> null
    }
    if (errorReason != null) {
      return ReleaseFetchResult(failure = unknown(errorReason))
    }
    val parsed = try {
      JsonCodec.parseJsonArrayStrict(response.body)
    } catch (_: ShellContentContractException) {
      return ReleaseFetchResult(failure = unknown("malformed GitHub Releases payload"))
    }
    return ReleaseFetchResult(releases = parsed)
  }

  private fun selectLatestRelease(releases: List<Any?>, includePrereleases: Boolean): ReleaseSelection {
    val parsedCandidates = releases.map { release -> releaseCandidate(release, includePrereleases) }
    val candidates = parsedCandidates.mapNotNull { it.candidate }
    val reason = when {
      releases.isEmpty() -> "no GitHub releases returned"
      parsedCandidates.any { it.malformedPayload } -> "malformed GitHub Releases payload"
      parsedCandidates.any { it.malformedEntry } -> "malformed release entry"
      candidates.isEmpty() -> "no usable semver GitHub release found"
      else -> null
    }
    if (reason != null) {
      return ReleaseSelection(failure = unknown(reason))
    }
    return ReleaseSelection(candidate = candidates.maxByOrNull { candidate -> candidate.version })
  }

  private fun releaseCandidate(release: Any?, includePrereleases: Boolean): ReleaseCandidateParseResult {
    val entry = JsonCodec.anyToStringAnyMap(release)
    if (entry == null) {
      return ReleaseCandidateParseResult(malformedPayload = true)
    }
    if (entry["draft"] as? Boolean ?: false) return ReleaseCandidateParseResult()
    if (!includePrereleases && entry["prerelease"] as? Boolean ?: false) {
      return ReleaseCandidateParseResult()
    }
    return candidateFromEntry(entry, includePrereleases)
  }

  private fun candidateFromEntry(entry: Map<String, Any?>, includePrereleases: Boolean): ReleaseCandidateParseResult {
    val tagName = entry["tag_name"] as? String
    val url = entry["html_url"] as? String
    val body = entry["body"] as? String
    val malformedEntry = tagName == null || url == null
    val version = tagName?.let(Semver::parse)
    val candidate = version
      ?.takeUnless { !includePrereleases && it.isPrerelease }
      ?.let { ReleaseCandidate(tagName = tagName, version = it, url = url.orEmpty(), body = body) }
    return ReleaseCandidateParseResult(candidate = candidate, malformedEntry = malformedEntry)
  }

  private data class ReleaseCandidateParseResult(
    val candidate: ReleaseCandidate? = null,
    val malformedPayload: Boolean = false,
    val malformedEntry: Boolean = false,
  )

  private data class ReleaseFetchResult(
    val releases: List<Any?>? = null,
    val failure: UpdateCheckResult? = null,
  )

  private data class ReleaseSelection(
    val candidate: ReleaseCandidate? = null,
    val failure: UpdateCheckResult? = null,
  )

  private data class ReleaseCandidate(
    val tagName: String,
    val version: Semver,
    val url: String,
    val body: String? = null,
  )

  companion object {
    private const val RELEASES_URL = "https://api.github.com/repos/oila-gmbh/skill-bill/releases"
    private const val USER_AGENT = "skill-bill-update-check"
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private val HTTP_SUCCESS_RANGE = 200..299
  }
}

private fun errorMessage(error: Exception): String =
  error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() }

private fun unknown(reason: String, installedVersion: String? = null): UpdateCheckResult = UpdateCheckResult(
  status = UpdateCheckStatus.UNKNOWN,
  installedVersion = installedVersion,
  reason = reason,
)
