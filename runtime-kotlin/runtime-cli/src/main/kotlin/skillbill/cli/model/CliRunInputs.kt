package skillbill.cli.model

import skillbill.ports.repository.RepositoryEnclosingRootPort
import java.nio.file.Path

data class CliRunInputs(
  val databasePath: String?,
  val stdinText: String?,
  val environment: Map<String, String>,
  val userHome: Path,
  val repositoryRoot: Path,
  val repositoryEnclosingRootPort: RepositoryEnclosingRootPort,
  val liveStdout: (String) -> Unit,
  val liveStderr: (String) -> Unit,
)
