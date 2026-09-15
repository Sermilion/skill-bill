package skillbill.ports.scaffold.install.model

import java.nio.file.Path

data class ScaffoldInstallLinkRequest(
  val repoRoot: Path,
  val installPaths: List<Path>,
)

data class ScaffoldInstallLinkResult(
  val installTargets: List<Path>,
)
