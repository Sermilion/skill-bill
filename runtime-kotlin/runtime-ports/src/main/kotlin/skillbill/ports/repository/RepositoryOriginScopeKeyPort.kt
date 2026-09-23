package skillbill.ports.repository

import java.nio.file.Path

sealed interface OriginScopeKey {
  data class Resolved(val key: String) : OriginScopeKey

  data class Unavailable(val reason: String) : OriginScopeKey
}

fun interface RepositoryOriginScopeKeyPort {
  fun resolveOriginScopeKey(repoRoot: Path): OriginScopeKey
}
