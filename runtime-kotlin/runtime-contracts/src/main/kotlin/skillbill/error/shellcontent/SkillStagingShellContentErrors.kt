package skillbill.error.shellcontent

class InternalSkillSidecarCollisionError(
  val parentSkillName: String,
  val internalSkillName: String,
  val sidecarRelativePath: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Internal skill '$internalSkillName' cannot be staged as sidecar " +
    "'$sidecarRelativePath' inside parent '$parentSkillName' skill directory: " +
    "another staged or authored file already claims that path. Rename or remove the conflicting file.",
  cause,
)

class InvalidAuthoredSkillSidecarError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidReviewSkillStructureError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class MissingContentFileError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class ComposedNativeAgentBudgetExceededError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class MissingRequiredSectionError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidDescriptorSectionError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidExecutionSectionError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidCeremonySectionError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class MissingShellCeremonyFileError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidSkillMdShapeError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class InvalidNativeAgentLinkInventorySchemaError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class MissingInstalledNativeAgentError(
  val logicalName: String,
  val provider: String,
  val expectedPath: String,
  val reason: String,
  val repairCommand: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Native agent '$logicalName' for provider '$provider' failed preflight at '$expectedPath': $reason. " +
    "Repair with: $repairCommand",
  cause,
)

class InvalidInternalSkillClassificationError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)

class MissingBaselinePlatformSelectionError(
  val selectingSlug: String,
  val requiredBaselineSlug: String,
  val declaringManifestPath: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Platform pack '$selectingSlug' declares a required baseline layer on '$requiredBaselineSlug' " +
    "(declared in '$declaringManifestPath'), but '$requiredBaselineSlug' is not in the selection. " +
    "Select '$requiredBaselineSlug' (or use platform mode ALL) so the baseline sidecar is present " +
    "at review time.",
  cause,
)

class InvalidFallbackCapabilityError(
  message: String,
  cause: Throwable? = null,
) : ShellContentContractException(message, cause)
