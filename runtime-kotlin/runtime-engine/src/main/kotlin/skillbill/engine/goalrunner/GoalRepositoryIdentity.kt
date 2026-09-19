package skillbill.engine.goalrunner

import skillbill.ports.repository.RepositoryEnclosingRootPort
import java.nio.file.Path

fun goalRepositoryIdentity(repoRoot: Path, repositoryEnclosingRootPort: RepositoryEnclosingRootPort): String =
  repositoryEnclosingRootPort.repositoryIdentity(repoRoot)
