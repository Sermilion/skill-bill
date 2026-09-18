package skillbill.infrastructure.sqlite.core

import java.nio.file.Path

internal class DatabaseWriteReadinessGate {
  @Volatile
  private var published: DatabaseIdentity? = null
  private val lock = Any()
  internal var schemaEstablishmentExecutions: Int = 0
    private set

  fun ensureReady(dbPath: Path) {
    val normalized = dbPath.toAbsolutePath().normalize()
    val cached = published
    val current = DatabaseIdentity.read(normalized)
    if (cached != null && current != null && cached.matches(current)) {
      return
    }
    synchronized(lock) {
      val cachedAgain = published
      val currentAgain = DatabaseIdentity.read(normalized)
      if (cachedAgain != null && currentAgain != null && cachedAgain.matches(currentAgain)) {
        return
      }
      published = null
      runCatching {
        schemaEstablishmentExecutions += 1
        DatabaseRuntime.establishSchemaReadiness(normalized)
        published = DatabaseIdentity.read(normalized)
          ?: error("Database readiness completed but identity could not be read at '$normalized'.")
      }.onFailure {
        published = null
      }.getOrThrow()
    }
  }
}
