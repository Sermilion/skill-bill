package skillbill.ports.validation.model

import skillbill.contracts.validation.ReleaseRefMetadataContract
import skillbill.contracts.validation.RepoValidationReportContract

data class RepoValidationReport(
  val issues: List<String>,
  val skillCount: Int,
  val addonCount: Int,
  val platformPackCount: Int,
  val nativeAgentCount: Int,
  val structuredIssues: List<RepoValidationIssue> = issues.map(RepoValidationIssue::fromRawIssue),
) {
  val passed: Boolean = issues.isEmpty()

  fun toContract(): RepoValidationReportContract = RepoValidationReportContract(
    passed = passed,
    skillCount = skillCount,
    addonCount = addonCount,
    platformPackCount = platformPackCount,
    nativeAgentCount = nativeAgentCount,
    issues = issues,
  )
}

data class RepoValidationIssue(
  val severity: RepoValidationIssueSeverity,
  val message: String,
  val sourcePath: String?,
  val code: String? = null,
  val name: String? = null,
  val exceptionName: String? = null,
) {
  companion object {
    fun fromRawIssue(raw: String): RepoValidationIssue {
      val separator = raw.indexOf(": ")
      return if (separator > 0 && raw.substring(0, separator).isNotBlank() &&
        !raw.substring(0, separator).contains(' ')
      ) {
        RepoValidationIssue(
          severity = RepoValidationIssueSeverity.ERROR,
          sourcePath = raw.substring(0, separator),
          message = raw.substring(separator + 2),
        )
      } else {
        RepoValidationIssue(
          severity = RepoValidationIssueSeverity.ERROR,
          sourcePath = null,
          message = raw,
        )
      }
    }
  }
}

enum class RepoValidationIssueSeverity {
  ERROR,
  WARNING,
  INFO,
}

data class ReleaseRefMetadata(
  val tag: String,
  val version: String,
  val major: Int,
  val minor: Int,
  val patch: Int,
  val prerelease: Boolean,
  val prereleaseIdentifier: String?,
  val buildMetadata: String?,
) {
  fun toContract(): ReleaseRefMetadataContract = ReleaseRefMetadataContract(
    tag = tag,
    version = version,
    major = major,
    minor = minor,
    patch = patch,
    prerelease = prerelease,
    prereleaseIdentifier = prereleaseIdentifier,
    buildMetadata = buildMetadata,
  )
}
