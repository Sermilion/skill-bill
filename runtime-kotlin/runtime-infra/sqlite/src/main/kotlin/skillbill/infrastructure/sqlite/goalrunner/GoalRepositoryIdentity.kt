package skillbill.infrastructure.sqlite.goalrunner

import skillbill.infrastructure.sqlite.core.InternalSqliteDiagnostics
import skillbill.infrastructure.sqlite.core.recordDegradedValue
import java.io.IOException
import java.nio.file.Path

internal fun goalRepositoryIdentity(repoRoot: Path): String {
  val canonical = try {
    repoRoot.toRealPath()
  } catch (error: IOException) {
    InternalSqliteDiagnostics.recordDegradedValue(
      seam = "goal_repository.identity",
      expected = "canonical repository real path",
      used = repoRoot.toAbsolutePath().normalize().toString(),
      error = error,
    )
    repoRoot.toAbsolutePath().normalize()
  }
  return "repo-root-realpath-v1:$canonical"
}
