package skillbill.infrastructure.launcher.codegraph

import java.nio.file.Path

internal object CodeGraphCommandBuilder {
  fun init(executable: String, repositoryRoot: Path): List<String> =
    listOf(executable, "init", repositoryRoot.toString(), "--yes")

  fun status(executable: String, repositoryRoot: Path): List<String> =
    listOf(executable, "status", repositoryRoot.toString())

  fun serve(executable: String): List<String> = listOf(executable, "serve", "--mcp")
}
