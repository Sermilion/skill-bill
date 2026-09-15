package skillbill.infrastructure.sqlite.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal data class DatabaseIdentity(
  val absolutePath: String,
  val userVersion: Int,
  val fileIdentity: String,
  val fileSizeBytes: Long,
) {
  fun matchesFile(path: Path): Boolean = read(path)?.let { current ->
    current.absolutePath == absolutePath &&
      current.userVersion == userVersion &&
      current.fileIdentity == fileIdentity &&
      current.fileSizeBytes >= fileSizeBytes
  } == true

  companion object {
    fun read(path: Path): DatabaseIdentity? {
      if (!Files.exists(path)) return null
      val normalized = path.toAbsolutePath().normalize()
      val attrs = Files.readAttributes(normalized, BasicFileAttributes::class.java)
      val userVersion = readUserVersion(normalized) ?: return null
      return DatabaseIdentity(
        absolutePath = normalized.toString(),
        userVersion = userVersion,
        fileIdentity = fileIdentity(attrs),
        fileSizeBytes = attrs.size(),
      )
    }

    private fun readUserVersion(path: Path): Int? = runCatching {
      DatabaseRuntime.openReadConnectionAt(path).use { database ->
        val connection = database.connection
        connection.createStatement().use { statement ->
          statement.executeQuery("PRAGMA user_version").use { rows ->
            if (!rows.next()) return@use null
            rows.getInt(1)
          }
        }
      }
    }.getOrNull()

    private fun fileIdentity(attributes: BasicFileAttributes): String = attributes.fileKey()?.toString()
      ?: "creation-time:${attributes.creationTime().toMillis()}"
  }
}
