package skillbill.ports.system

import java.nio.file.Path

fun interface CheckedOutBranchSource {
  fun checkedOutBranch(repoRoot: Path): String?
}
