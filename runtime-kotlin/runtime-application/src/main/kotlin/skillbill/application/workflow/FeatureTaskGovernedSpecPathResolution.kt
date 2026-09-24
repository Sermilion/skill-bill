package skillbill.application.workflow

import skillbill.application.workflow.model.FeatureTaskGovernedSpecPathResult
import skillbill.ports.repository.RepositoryEnclosingRootPort
import java.nio.file.Path

fun resolveFeatureTaskGovernedSpecPath(
  repositoryEnclosingRootPort: RepositoryEnclosingRootPort,
  repositoryRoot: Path,
  specPath: Path,
): FeatureTaskGovernedSpecPathResult {
  val root =
    repositoryEnclosingRootPort.canonicalPath(
      repositoryEnclosingRootPort.enclosingRepositoryRoot(repositoryRoot),
    )
  val absolute = if (specPath.isAbsolute) specPath else root.resolve(specPath)
  val resolved = repositoryEnclosingRootPort.canonicalPath(absolute)
  if (!resolved.startsWith(root)) {
    return FeatureTaskGovernedSpecPathResult.OutsideRepository(root)
  }
  val relative = root.relativize(resolved).joinToString("/") { it.toString() }
  if (!validGovernedSpecPath(relative)) {
    return FeatureTaskGovernedSpecPathResult.InvalidGovernedPath
  }
  return FeatureTaskGovernedSpecPathResult.Ok(relative)
}

private fun validGovernedSpecPath(value: String): Boolean =
  value.startsWith(".feature-specs/") &&
    value.endsWith(".md") &&
    value.none { it == '\r' || it == '\n' } &&
    value.split('/').none { it.isBlank() || it == "." || it == ".." }
