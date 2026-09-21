package skillbill.infrastructure.skills.scaffold.runtime.validation
import java.nio.file.Path

internal object RepoValidationRuntimeReleasePolicy {
  internal const val NORMALIZED_MIT_LICENSE_SHA256 =
    "7f6d941b05cd24c92bdfe2d35fa2c6729d638edeab1d42d10774e07c8061fbfa"
  private val semverTagPattern =
    Regex(
      "^v(?<major>0|[1-9]\\d*)\\.(?<minor>0|[1-9]\\d*)\\.(?<patch>0|[1-9]\\d*)" +
        "(?:-(?<prerelease>(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)" +
        "(?:\\.(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*))*))?" +
        "(?:\\+(?<build>[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$",
    )

  fun parseReleaseRef(rawValue: String): ReleaseRefMetadata {
    val candidate = rawValue.trim().removePrefix("refs/tags/")
    val match = semverTagPattern.matchEntire(candidate)
      ?: throw IllegalArgumentException(
        "Release tag must match canonical vMAJOR.MINOR.PATCH with optional SemVer prerelease/build metadata.",
      )
    return ReleaseRefMetadata(
      tag = candidate,
      version = candidate.removePrefix("v"),
      major = match.groups["major"]!!.value.toInt(),
      minor = match.groups["minor"]!!.value.toInt(),
      patch = match.groups["patch"]!!.value.toInt(),
      prerelease = match.groups["prerelease"] != null,
      prereleaseIdentifier = match.groups["prerelease"]?.value,
      buildMetadata = match.groups["build"]?.value,
    )
  }

  fun validateReleaseRef(repoRoot: Path, rawValue: String, forcePrerelease: Boolean = false): ReleaseRefMetadata {
    val parsed = parseReleaseRef(rawValue)
    if (forcePrerelease && !parsed.prerelease) {
      throw ReleaseLicensePolicyError(
        "Manual staging references must carry a SemVer prerelease identifier; " +
          "stable tags cannot be forced into staging.",
      )
    }
    validateReleaseLicensePolicy(repoRoot.toAbsolutePath().normalize(), parsed)
    return parsed
  }
}
