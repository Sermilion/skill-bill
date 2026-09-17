package skillbill.infrastructure.fs.install.plan

import skillbill.error.InvalidInternalSkillClassificationError
import skillbill.infrastructure.fs.install.staging.StagedSymlinkTargetInput
import skillbill.infrastructure.fs.install.staging.resolveStagedSymlinkTarget
import skillbill.infrastructure.fs.install.support.claudeConfigRoot
import skillbill.infrastructure.fs.install.support.claudeConfigRoots
import skillbill.infrastructure.fs.install.support.claudeSkillTargets
import skillbill.infrastructure.fs.install.support.codexConfigRoot
import skillbill.infrastructure.fs.install.support.codexConfigRoots
import skillbill.infrastructure.fs.install.support.codexSkillTargets
import skillbill.infrastructure.fs.scaffold.authoring.parseInternalForFrontmatter
import skillbill.install.model.AgentTarget
import skillbill.install.model.InstallAgent
import skillbill.install.model.InstallPlanSkill
import skillbill.install.model.InstallTransaction
import skillbill.model.toPath
import skillbill.ports.repository.toFileLocation
import skillbill.scaffold.model.PlatformManifest
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import skillbill.infrastructure.fs.nativeagent.support.detectCodexAgentsTargets as nativeDetectCodexAgentsTargets

internal val SUPPORTED_AGENTS: List<String> = InstallAgent.supportedIds
internal const val CODEX_AGENTS_KIND: String = "codex-agents"
internal const val CLAUDE_AGENTS_KIND: String = "claude-agents"
internal const val JUNIE_AGENTS_KIND: String = "junie-agents"
internal const val CURSOR_AGENTS_KIND: String = "cursor-agents"

internal fun agentPaths(home: Path? = null, environment: Map<String, String> = System.getenv()): Map<String, Path> {
  val resolvedHome = home ?: Path.of(System.getProperty("user.home"))
  return mapOf(
    "claude" to claudeConfigRoot(resolvedHome, environment).resolve("skills"),
    "junie" to resolvedHome.resolve(".junie/skills"),
    "cursor" to resolvedHome.resolve(".cursor/skills"),
    "codex" to codexConfigRoot(resolvedHome, environment).resolve("skills"),
  )
}

internal fun codexAgentsPath(home: Path? = null, environment: Map<String, String> = System.getenv()): Path {
  val resolvedHome = home ?: Path.of(System.getProperty("user.home"))
  return codexConfigRoot(resolvedHome, environment).resolve("agents")
}

internal fun detectAgents(home: Path? = null, environment: Map<String, String> = System.getenv()): List<AgentTarget> {
  val resolvedHome = home ?: Path.of(System.getProperty("user.home"))
  return SUPPORTED_AGENTS.flatMap { agent ->
    if (agent == "claude") {
      if (agentIsPresent(resolvedHome, agent, agentPaths(resolvedHome, environment).getValue(agent), environment)) {
        claudeSkillTargets(resolvedHome, environment).map { path -> AgentTarget("claude", path.toFileLocation()) }
      } else {
        emptyList()
      }
    } else if (agent == "codex") {
      if (agentIsPresent(resolvedHome, agent, agentPaths(resolvedHome, environment).getValue(agent), environment)) {
        codexSkillTargets(resolvedHome, environment).map { path -> AgentTarget("codex", path.toFileLocation()) }
      } else {
        emptyList()
      }
    } else {
      val path = agentPaths(resolvedHome, environment).getValue(agent)
      if (agentIsPresent(resolvedHome, agent, path, environment)) {
        listOf(AgentTarget(agent, path.toFileLocation()))
      } else {
        emptyList()
      }
    }
  }
}

internal fun detectCodexAgentsTargets(
  home: Path? = null,
  environment: Map<String, String> = System.getenv(),
): List<AgentTarget> {
  val resolvedHome = home ?: Path.of(System.getProperty("user.home"))
  if (!agentIsPresent(resolvedHome, "codex", agentPaths(resolvedHome, environment).getValue("codex"), environment)) {
    return emptyList()
  }
  return nativeDetectCodexAgentsTargets(resolvedHome, environment)
    .map { target -> AgentTarget(target.name, target.path.toFileLocation()) }
}

internal data class InstallContext(
  val repoRoot: Path? = null,
  val home: Path = Path.of(System.getProperty("user.home")),
  val manifests: List<PlatformManifest>? = null,
  val selectedPackSkills: List<InstallPlanSkill> = emptyList(),
  val selectedPlatformSlugs: Set<String> = emptySet(),
)

internal data class InstallSkillOutcome(
  val linkPaths: List<Path>,
  val transaction: InstallTransaction?,
)

internal fun installSkill(
  skillPath: Path,
  agentTargets: Iterable<AgentTarget>,
  transaction: InstallTransaction? = null,
  context: InstallContext = InstallContext(),
): InstallSkillOutcome {
  val resolvedSkill = skillPath.toAbsolutePath().normalize()
  if (!Files.isDirectory(resolvedSkill)) {
    throw FileNotFoundException("Skill directory '$resolvedSkill' does not exist.")
  }
  parseInternalForFrontmatter(resolvedSkill.resolve("content.md"))?.let { declaredParent ->
    throw InvalidInternalSkillClassificationError(
      "Skill '${resolvedSkill.fileName}' declares 'internal-for: $declaredParent' and cannot be " +
        "installed or linked directly: internal skills install as '<skill-name>.md' sidecars inside " +
        "their parent's installed directory. Install the parent skill instead.",
    )
  }
  val symlinkTarget = resolveStagedSymlinkTarget(
    StagedSymlinkTargetInput(
      resolvedSkill = resolvedSkill,
      repoRoot = context.repoRoot,
      home = context.home,
      manifests = context.manifests,
      selectedPackSkills = context.selectedPackSkills,
      selectedPlatformSlugs = context.selectedPlatformSlugs,
    ),
  )
  val created = mutableListOf<Path>()
  var currentTransaction = transaction
  for (target in agentTargets) {
    val outcome = installSkillSymlink(resolvedSkill, symlinkTarget, target, currentTransaction)
    outcome.linkPath?.let(created::add)
    currentTransaction = outcome.transaction
  }
  return InstallSkillOutcome(created, currentTransaction)
}

private data class InstallSkillSymlinkOutcome(
  val linkPath: Path?,
  val transaction: InstallTransaction?,
)

private fun installSkillSymlink(
  resolvedSkill: Path,
  symlinkTarget: Path,
  target: AgentTarget,
  transaction: InstallTransaction?,
): InstallSkillSymlinkOutcome {
  Files.createDirectories(target.path.toPath())
  val linkPath = target.path.toPath().resolve(resolvedSkill.fileName)
  if (Files.isSymbolicLink(linkPath)) {
    val existingTarget = runCatching { Files.readSymbolicLink(linkPath).toAbsolutePath().normalize() }.getOrNull()
    if (existingTarget == symlinkTarget) {
      return InstallSkillSymlinkOutcome(null, transaction)
    }
    Files.deleteIfExists(linkPath)
  } else if (Files.exists(linkPath)) {
    Files.delete(linkPath)
  }
  Files.createSymbolicLink(linkPath, symlinkTarget)
  val updatedTransaction = transaction?.withRecordedSymlink(linkPath.toFileLocation())
  return InstallSkillSymlinkOutcome(linkPath, updatedTransaction)
}

internal fun uninstallTargets(createdSymlinks: Iterable<Path>): List<Path> {
  val removed = mutableListOf<Path>()
  for (linkPath in createdSymlinks) {
    if (Files.isSymbolicLink(linkPath) || Files.exists(linkPath)) {
      Files.deleteIfExists(linkPath)
      removed.add(linkPath)
    }
  }
  return removed
}

private fun agentIsPresent(
  home: Path,
  agent: String,
  installPath: Path,
  environment: Map<String, String> = System.getenv(),
): Boolean {
  if (Files.exists(installPath)) {
    return true
  }
  val roots = when (agent) {
    "claude" -> claudeConfigRoots(home, environment)
    "junie" -> listOf(home.resolve(".junie"))
    "cursor" -> listOf(home.resolve(".cursor"))
    "codex" -> {
      val roots = codexConfigRoots(home, environment)
      if (roots.isNotEmpty()) roots else listOf(home.resolve(".codex"), home.resolve(".agents"))
    }
    else -> emptyList()
  }
  return roots.any(Files::exists)
}
