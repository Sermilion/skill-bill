package skillbill.ports.repository

import skillbill.ports.repository.model.OriginScopeKey
import java.nio.file.Path

fun interface RepositoryOriginScopeKeyPort {
  fun resolveOriginScopeKey(repoRoot: Path): OriginScopeKey
}
