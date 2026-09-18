package skillbill.infrastructure.sqlite.core

import skillbill.error.UnresolvedEnvironmentContextFieldError
import skillbill.model.EnvironmentContext
import java.nio.file.Path
import java.nio.file.Paths

internal object DatabasePaths {
  const val DB_ENVIRONMENT_KEY: String = "SKILL_BILL_REVIEW_DB"

  fun defaultDbPath(userHome: Path): Path = userHome.resolve(".skill-bill").resolve("review-metrics.db")

  fun resolveDbPath(cliValue: String?, environment: Map<String, String>, userHome: Path): Path {
    val candidate = cliValue ?: environment[DB_ENVIRONMENT_KEY]
    return if (candidate != null) {
      expandUserPath(candidate = candidate, userHome = userHome)
    } else {
      defaultDbPath(userHome).toAbsolutePath().normalize()
    }
  }

  private fun expandUserPath(candidate: String, userHome: Path): Path {
    val expandedCandidate =
      when {
        candidate == "~" -> userHome.toString()
        candidate.startsWith("~/") || candidate.startsWith("~\\") ->
          userHome.resolve(candidate.drop(2)).toString()
        else -> candidate
      }
    return Paths.get(expandedCandidate).toAbsolutePath().normalize()
  }
}

internal fun requireResolvedEnvironmentContext(context: EnvironmentContext): EnvironmentContext {
  if (context.userHome == EnvironmentContext.UnspecifiedUserHome) {
    throw UnresolvedEnvironmentContextFieldError("userHome")
  }
  if (context.environment === EnvironmentContext.UnspecifiedEnvironment) {
    throw UnresolvedEnvironmentContextFieldError("environment")
  }
  return context.copy(
    userHome = context.userHome.toAbsolutePath().normalize(),
  )
}
