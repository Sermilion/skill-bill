package skillbill.infrastructure.skills.install.mcp

import skillbill.infrastructure.host.jvm.resolveUserHome
import skillbill.infrastructure.skills.install.plan.codexConfigRoots
import skillbill.infrastructure.skills.nativeagent.support.claudeConfigRoots
import skillbill.install.model.ClaudeMcpProfileFailure
import skillbill.install.model.InstallAgent
import skillbill.install.model.McpMutationResult
import skillbill.install.model.McpProfileOutcome
import skillbill.ports.repository.toFileLocation
import java.nio.file.Path

object McpRegistrationOperations {
  fun register(
    agent: String,
    runtimeMcpBin: Path,
    home: Path? = null,
    environment: Map<String, String>,
  ): McpMutationResult {
    val resolvedHome = home ?: resolveUserHome(null)
    val command = runtimeMcpBin.toAbsolutePath().normalize().toString()
    return when (val installAgent = InstallAgent.fromId(agent)) {
      InstallAgent.CLAUDE -> claudeFanOut(agent, resolvedHome, environment) { perProfilePath ->
        McpJsonConfig.register(agent, perProfilePath, command)
      }
      InstallAgent.CODEX -> codexFanOut(agent, resolvedHome, environment) { perProfilePath ->
        McpTomlConfig.register(agent, perProfilePath, command)
      }
      InstallAgent.JUNIE -> McpJsonConfig.register(agent, configPathFor(installAgent, resolvedHome), command)
      InstallAgent.CURSOR -> McpJsonConfig.register(agent, configPathFor(installAgent, resolvedHome), command)
    }
  }

  fun unregister(agent: String, home: Path? = null, environment: Map<String, String>): McpMutationResult {
    val resolvedHome = home ?: resolveUserHome(null)
    return when (val installAgent = InstallAgent.fromId(agent)) {
      InstallAgent.CLAUDE -> claudeFanOut(agent, resolvedHome, environment) { perProfilePath ->
        McpJsonConfig.unregister(agent, perProfilePath)
      }
      InstallAgent.CODEX -> codexFanOut(agent, resolvedHome, environment) { perProfilePath ->
        McpTomlConfig.unregister(agent, perProfilePath)
      }
      InstallAgent.JUNIE -> McpJsonConfig.unregister(agent, configPathFor(installAgent, resolvedHome))
      InstallAgent.CURSOR -> McpJsonConfig.unregister(agent, configPathFor(installAgent, resolvedHome))
    }
  }

  fun configFormatFor(agent: InstallAgent): McpConfigFormat =
    if (agent.mcpUsesToml) McpConfigFormat.TOML else McpConfigFormat.JSON

  fun configPathFor(agent: InstallAgent, home: Path): Path = home.resolve(agent.mcpConfigRelativePath)

  private fun claudeProfileConfigPaths(home: Path, environment: Map<String, String>): List<Path> {
    val defaultRoot = home.resolve(requireNotNull(InstallAgent.CLAUDE.profileDirectoryPrefix).removeSuffix("-"))
      .toAbsolutePath()
      .normalize()
    return claudeConfigRoots(home, environment).map { root ->
      if (root == defaultRoot) {
        home.resolve(InstallAgent.CLAUDE.mcpProfileFileName)
      } else {
        root.resolve(InstallAgent.CLAUDE.mcpProfileFileName)
      }
    }
  }

  private fun codexProfileConfigPaths(home: Path, environment: Map<String, String>): List<Path> {
    val roots = codexConfigRoots(home, environment)
    return if (roots.isNotEmpty()) {
      roots.map { root -> root.resolve(InstallAgent.CODEX.mcpProfileFileName) }
    } else {
      listOf(home.resolve(InstallAgent.CODEX.mcpConfigRelativePath))
    }
  }

  private fun claudeFanOut(
    agent: String,
    home: Path,
    environment: Map<String, String>,
    mutate: (Path) -> McpMutationResult,
  ): McpMutationResult = profileFanOut(
    agent = agent,
    profilePaths = claudeProfileConfigPaths(home, environment),
    representativePath = home.resolve(InstallAgent.CLAUDE.mcpConfigRelativePath),
    failureLabel = "Claude",
    mutate = mutate,
  )

  private fun codexFanOut(
    agent: String,
    home: Path,
    environment: Map<String, String>,
    mutate: (Path) -> McpMutationResult,
  ): McpMutationResult = profileFanOut(
    agent = agent,
    profilePaths = codexProfileConfigPaths(home, environment),
    representativePath = home.resolve(InstallAgent.CODEX.mcpConfigRelativePath),
    failureLabel = "Codex",
    mutate = mutate,
  )

  private fun profileFanOut(
    agent: String,
    profilePaths: List<Path>,
    representativePath: Path,
    failureLabel: String,
    mutate: (Path) -> McpMutationResult,
  ): McpMutationResult {
    val outcomes = mutableListOf<McpProfileOutcome>()
    val failures = mutableListOf<Pair<Path, Throwable>>()

    profilePaths.forEach { perProfilePath ->
      runCatching { mutate(perProfilePath) }
        .onSuccess { result -> outcomes.add(McpProfileOutcome(result.configPath, result.changed)) }
        .onFailure { error -> failures.add(perProfilePath to error) }
    }

    if (failures.isNotEmpty()) {
      val names = failures.joinToString("; ") { (path, error) -> "$path: ${error.message}" }
      throw ClaudeMcpProfileFailure(
        "Failed to update $failureLabel MCP config for profile(s): $names",
        succeeded = outcomes.toList(),
      )
    }

    return McpMutationResult(
      agent = agent,
      configPath = representativePath.toFileLocation(),
      changed = outcomes.any { it.changed },
      profiles = outcomes,
    )
  }
}

enum class McpConfigFormat {
  JSON,
  TOML,
}
