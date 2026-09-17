package skillbill.infrastructure.fs.launcher.process

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal fun newSha256Digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

internal fun sha256Bytes(bytes: ByteArray): ByteArray = newSha256Digest().digest(bytes)

internal fun sha256Hex(bytes: ByteArray): String =
  sha256Bytes(bytes).joinToString("") { byte -> "%02x".format(byte) }

internal fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

internal fun sha256HexOfFile(path: Path): String = sha256Hex(Files.readAllBytes(path))

internal fun atomicWriteBytes(path: Path, bytes: ByteArray) {
  val parent = path.parent
  if (parent != null) {
    Files.createDirectories(parent)
  }
  val tempDir = parent ?: Path.of(".")
  val temp = Files.createTempFile(tempDir, "${path.fileName}.", ".tmp")
  try {
    Files.write(temp, bytes)
    FileChannel.open(temp, StandardOpenOption.WRITE).use { channel -> channel.force(true) }
    atomicMoveReplacing(temp, path)
  } finally {
    Files.deleteIfExists(temp)
  }
}

internal fun atomicWriteString(path: Path, text: String) {
  atomicWriteBytes(path, text.toByteArray())
}

internal fun atomicMoveReplacing(source: Path, target: Path) {
  try {
    Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
  } catch (_: AtomicMoveNotSupportedException) {
    Files.move(source, target, REPLACE_EXISTING)
  }
}

internal fun replaceDirectory(
  source: Path,
  target: Path,
  onDegraded: (String) -> Unit,
) {
  try {
    Files.move(source, target, ATOMIC_MOVE)
  } catch (error: AtomicMoveNotSupportedException) {
    onDegraded("AtomicMoveNotSupportedException: ${error.message.orEmpty()}")
    deleteRecursively(target)
    Files.move(source, target)
  } catch (error: FileAlreadyExistsException) {
    onDegraded("FileAlreadyExistsException: ${error.message.orEmpty()}")
  } catch (error: FileSystemException) {
    onDegraded("${error::class.simpleName.orEmpty()}: ${error.message.orEmpty()}")
    deleteRecursively(target)
    Files.move(source, target)
  }
}

internal fun deleteRecursively(root: Path) {
  if (!Files.exists(root)) return
  Files.walk(root).use { stream ->
    stream.toList().sortedDescending().forEach { Files.deleteIfExists(it) }
  }
}

internal fun rollbackRestoreBytes(path: Path, bytes: ByteArray) {
  atomicWriteBytes(path, bytes)
}

internal fun rollbackDeleteIfExists(path: Path) {
  Files.deleteIfExists(path)
}

internal fun rollbackDeleteEmptyDirectory(path: Path) {
  if (Files.isDirectory(path) && Files.list(path).use { stream -> !stream.findAny().isPresent }) {
    Files.deleteIfExists(path)
  }
}

internal fun rollbackDeleteRegularFileOrSymlink(path: Path) {
  if (Files.isSymbolicLink(path) || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
    Files.deleteIfExists(path)
  }
}

internal fun rollbackDeletePathEntry(target: Path) {
  if (Files.isSymbolicLink(target)) {
    Files.deleteIfExists(target)
    return
  }
  if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
    deleteRecursively(target)
    return
  }
  Files.deleteIfExists(target)
}

internal fun requirePathContainedIn(candidate: Path, root: Path, lazyMessage: () -> Any) {
  require(pathContainedIn(candidate, root), lazyMessage)
}

internal fun pathContainedIn(candidate: Path, root: Path): Boolean {
  val normalizedCandidate = candidate.toAbsolutePath().normalize()
  val normalizedRoot = root.toAbsolutePath().normalize()
  if (Files.isSymbolicLink(normalizedCandidate)) return false
  val realRoot = resolveExistingAncestor(normalizedRoot)
  val realCandidate = resolveExistingAncestor(normalizedCandidate)
  return realCandidate.startsWith(realRoot)
}

private fun resolveExistingAncestor(path: Path): Path = try {
  path.toRealPath()
} catch (error: NoSuchFileException) {
  val parent = path.parent ?: throw error
  resolveExistingAncestor(parent).resolve(path.fileName)
}
