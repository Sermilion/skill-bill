package skillbill.workflow

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

fun writeDecompositionManifestText(target: Path, content: String) {
  Files.createDirectories(target.parent)
  val temp = Files.createTempFile(target.parent, "${target.fileName}.", ".tmp")
  Files.writeString(temp, content)
  try {
    Files.move(temp, target, REPLACE_EXISTING, ATOMIC_MOVE)
  } catch (_: AtomicMoveNotSupportedException) {
    Files.move(temp, target, REPLACE_EXISTING)
  }
}
