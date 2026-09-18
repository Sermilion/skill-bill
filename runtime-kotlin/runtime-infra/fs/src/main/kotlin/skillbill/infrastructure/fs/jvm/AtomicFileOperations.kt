package skillbill.infrastructure.fs.jvm

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal fun atomicMoveReplacing(source: Path, target: Path) {
  try {
    Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
  } catch (_: AtomicMoveNotSupportedException) {
    Files.move(source, target, REPLACE_EXISTING)
  }
}
