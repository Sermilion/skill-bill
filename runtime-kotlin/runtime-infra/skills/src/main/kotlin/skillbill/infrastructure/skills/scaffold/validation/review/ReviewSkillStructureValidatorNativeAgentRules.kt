package skillbill.infrastructure.skills.scaffold.validation.review
import skillbill.error.shellcontent.InvalidReviewSkillStructureError
import java.nio.file.Path

internal fun violation(
  path: Path,
  rule: String,
) = ReviewSkillStructureViolation(path, rule)

internal fun invalidNativeAgentBundle(
  path: Path,
  error: Exception,
): Nothing =
  throw InvalidReviewSkillStructureError(
    "$path: invalid native-agent source bundle: ${error.message}",
    error,
  )
