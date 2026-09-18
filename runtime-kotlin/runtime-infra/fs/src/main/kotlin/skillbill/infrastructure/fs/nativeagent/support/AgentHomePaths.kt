package skillbill.infrastructure.fs.nativeagent.support

import skillbill.install.model.SupportedAgent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

const val CLAUDE_CONFIG_DIR_ENV: String = "CLAUDE_CONFIG_DIR"

private const val CODEX_HOME_ENV: String = "CODEX_HOME"
private val CLAUDE_PROFILE_MARKERS: List<String> =
  listOf(".claude.json", ".credentials.json", "commands", "agents", "history.jsonl")

private val CODEX_PROFILE_MARKERS: List<String> =
  listOf("config.toml", "history.jsonl", "installation_id", "router.config.toml")

fun claudeConfigRoots(home: Path, environment: Map<String, String>): List<Path> {
  val ordered = mutableListOf<Path>()
  val seen = mutableSetOf<Path>()

  fun add(path: Path) {
    val normalized = path.toAbsolutePath().normalize()
    if (seen.add(normalized)) {
      ordered.add(normalized)
    }
  }

  add(home.resolve(".claude"))

  if (Files.isDirectory(home)) {
    runCatching {
      Files.list(home).use { stream ->
        stream
          .filter { entry -> Files.isDirectory(entry) }
          .filter { entry ->
            entry.fileName.toString().startsWith(requireNotNull(SupportedAgent.CLAUDE.profileDirectoryPrefix))
          }
          .filter { entry -> hasClaudeProfileMarker(entry) }
          .sorted(compareBy { entry -> entry.fileName.toString() })
          .toList()
      }
    }.getOrDefault(emptyList()).forEach { entry -> add(entry) }
  }

  environment[CLAUDE_CONFIG_DIR_ENV]?.takeIf { it.isNotBlank() }?.let { explicit ->
    add(Path.of(explicit))
  }

  return ordered
}

fun codexAgentsTargets(home: Path, environment: Map<String, String>): List<Path> {
  val resolvedHome = home
  val ordered = mutableListOf<Path>()
  val seen = mutableSetOf<Path>()

  fun add(path: Path) {
    val normalized = path.toAbsolutePath().normalize()
    if (seen.add(normalized)) {
      ordered.add(normalized)
    }
  }

  codexConfigRoots(resolvedHome, environment).forEach { root -> add(root.resolve("agents")) }
  add(resolvedHome.resolve(".agents/agents"))
  return ordered
}

data class NativeAgentHomeTarget(
  val name: String,
  val path: Path,
)

fun detectCodexAgentsTargets(home: Path, environment: Map<String, String>): List<NativeAgentHomeTarget> {
  val resolvedHome = home
  if (!codexAgentIsPresent(resolvedHome, environment)) {
    return emptyList()
  }
  return codexAgentsTargets(resolvedHome, environment)
    .map { path -> NativeAgentHomeTarget(SupportedAgent.CODEX.nativeAgentsKind, path) }
}

private fun codexConfigRoots(home: Path, environment: Map<String, String>): List<Path> {
  val ordered = mutableListOf<Path>()
  val seen = mutableSetOf<Path>()

  fun add(path: Path) {
    val normalized = path.toAbsolutePath().normalize()
    if (seen.add(normalized)) {
      ordered.add(normalized)
    }
  }

  val defaultCodex = home.resolve(".codex")
  if (Files.exists(defaultCodex)) {
    add(defaultCodex)
  }

  if (Files.isDirectory(home)) {
    runCatching {
      Files.list(home).use { stream ->
        stream
          .filter { entry -> Files.isDirectory(entry) }
          .filter { entry ->
            entry.fileName.toString().startsWith(requireNotNull(SupportedAgent.CODEX.profileDirectoryPrefix))
          }
          .filter { entry -> hasCodexProfileMarker(entry) }
          .sorted(compareBy { entry -> entry.fileName.toString() })
          .toList()
      }
    }.getOrDefault(emptyList()).forEach { entry -> add(entry) }
  }

  environment[CODEX_HOME_ENV]?.takeIf { it.isNotBlank() }?.let { explicit ->
    add(Path.of(explicit))
  }

  return ordered
}

private fun codexConfigRoot(home: Path, environment: Map<String, String>): Path =
  environment[CODEX_HOME_ENV]?.takeIf { it.isNotBlank() }
    ?.let { Path.of(it).toAbsolutePath().normalize() }
    ?: defaultCodexRoot(home)

private fun defaultCodexRoot(home: Path): Path {
  val codexRoot = home.resolve(".codex")
  return if (Files.exists(codexRoot)) codexRoot else home.resolve(".agents")
}

private fun codexAgentIsPresent(home: Path, environment: Map<String, String>): Boolean {
  val installPath = codexConfigRoot(home, environment).resolve("skills")
  if (Files.exists(installPath)) {
    return true
  }
  val roots = codexConfigRoots(home, environment)
  val candidates = if (roots.isNotEmpty()) roots else listOf(home.resolve(".codex"), home.resolve(".agents"))
  return candidates.any(Files::exists)
}

private fun hasClaudeProfileMarker(root: Path): Boolean =
  CLAUDE_PROFILE_MARKERS.any { marker -> Files.exists(root.resolve(marker)) }

private fun hasCodexProfileMarker(root: Path): Boolean =
  CODEX_PROFILE_MARKERS.any { marker -> Files.exists(root.resolve(marker)) }
