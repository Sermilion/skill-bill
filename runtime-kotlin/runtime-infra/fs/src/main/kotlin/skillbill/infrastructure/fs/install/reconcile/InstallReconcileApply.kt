package skillbill.infrastructure.fs.install.reconcile

import skillbill.error.ReconciliationConflictError
import skillbill.infrastructure.fs.jvm.atomicMoveReplacing
import skillbill.install.model.BaselineManifest
import skillbill.install.model.ReconciliationPlan
import skillbill.install.model.SkillReconciliationOutcome
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal fun applyReconciliation(
  upstream: ReconcileSourceRoots,
  local: ReconcileSourceRoots,
  home: Path,
  baseline: BaselineManifest,
): ReconcileApplyOutput {
  val upstreamSkills = enumerateSkills(upstream, home, ReconcileSourceSide.UPSTREAM)
  val localSkills = enumerateSkills(local, home, ReconcileSourceSide.LOCAL)
  val plan = classifyReconciliation(upstreamSkills, localSkills, baseline)
  guardPruneAgainstEmptyUpstream(plan, upstreamSkills)

  val installedPaths = mutableListOf<String>()
  plan.outcomes.forEach { outcome ->
    if (!outcomeInstallsUpstream(outcome)) {
      return@forEach
    }
    val skillPath = outcome.skillRelativePath
    val upstreamDir = upstreamSkills[skillPath]?.sourceDir
      ?: throw ReconciliationConflictError(
        skillRelativePath = skillPath,
        reason = "apply requires the upstream skill dir but it was not enumerated.",
      )
    val liveDir = liveSkillDir(local, skillPath)
    reconcileSkillDirectory(upstreamDir, liveDir)
    installedPaths.add(skillPath)
  }

  val prunedPaths = plan.outcomes
    .filterIsInstance<SkillReconciliationOutcome.Prune>()
    .map { outcome ->
      deleteTreeRecursively(liveSkillDir(local, outcome.skillRelativePath))
      outcome.skillRelativePath
    }
  adoptPlatformPackNonSkillFiles(upstream, local, upstreamSkills)
  return ReconcileApplyOutput(plan = plan, installedPaths = installedPaths, prunedPaths = prunedPaths)
}

private fun guardPruneAgainstEmptyUpstream(
  plan: ReconciliationPlan,
  upstreamSkills: Map<String, ReconcileSkillEntry>,
) {
  if (upstreamSkills.isNotEmpty()) {
    return
  }
  val pruned = plan.prunedPaths
  if (pruned.isNotEmpty()) {
    throw ReconciliationConflictError(
      skillRelativePath = pruned.first(),
      reason = "refusing to prune ${pruned.size} installed path(s) because the upstream source " +
        "tree enumerated no skills at all; the candidate source is missing or incomplete.",
    )
  }
}

private fun adoptPlatformPackNonSkillFiles(
  upstream: ReconcileSourceRoots,
  local: ReconcileSourceRoots,
  upstreamSkills: Map<String, ReconcileSkillEntry>,
) {
  val upstreamPacks = upstream.platformPacksRoot.toAbsolutePath().normalize()
  if (!Files.isDirectory(upstreamPacks)) {
    return
  }
  val packSkillDirs = upstreamSkills.values
    .map { it.sourceDir }
    .filter { it.startsWith(upstreamPacks) }
  val livePacks = local.platformPacksRoot.toAbsolutePath().normalize()
  Files.walk(upstreamPacks).use { stream ->
    stream.forEach { path ->
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        return@forEach
      }
      if (packSkillDirs.any { skillDir -> path.startsWith(skillDir) }) {
        return@forEach
      }
      val dest = livePacks.resolve(upstreamPacks.relativize(path).toString())
      dest.parent?.let(Files::createDirectories)
      Files.copy(
        path,
        dest,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES,
        LinkOption.NOFOLLOW_LINKS,
      )
    }
  }
  deleteLivePackFilesAbsentUpstream(upstreamPacks, livePacks, local, upstreamSkills)
}

private fun deleteLivePackFilesAbsentUpstream(
  upstreamPacks: Path,
  livePacks: Path,
  local: ReconcileSourceRoots,
  upstreamSkills: Map<String, ReconcileSkillEntry>,
) {
  if (!Files.isDirectory(livePacks)) {
    return
  }
  val liveSkillDirs = upstreamSkills.keys
    .filter { it.startsWith(PLATFORM_PACKS_PREFIX) }
    .map { liveSkillDir(local, it) }
  Files.walk(livePacks).use { stream ->
    stream.filter { !Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }.forEach { path ->
      if (liveSkillDirs.any { skillDir -> path.startsWith(skillDir) }) {
        return@forEach
      }
      if (!Files.exists(upstreamPacks.resolve(livePacks.relativize(path).toString()), LinkOption.NOFOLLOW_LINKS)) {
        Files.deleteIfExists(path)
      }
    }
  }
  Files.walk(livePacks).use { stream ->
    stream.sorted(Comparator.reverseOrder()).forEach { path ->
      if (path != livePacks && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        runCatching { Files.delete(path) }
      }
    }
  }
}

private fun outcomeInstallsUpstream(outcome: SkillReconciliationOutcome): Boolean = when (outcome) {
  is SkillReconciliationOutcome.Adopt -> true
  is SkillReconciliationOutcome.Unchanged -> false
  is SkillReconciliationOutcome.Prune -> false
  is SkillReconciliationOutcome.LocallyAuthored -> false
}

private fun liveSkillDir(local: ReconcileSourceRoots, skillRelativePath: String): Path = when {
  skillRelativePath.startsWith(SKILLS_PREFIX) ->
    local.skillsRoot.resolve(skillRelativePath.removePrefix(SKILLS_PREFIX))
  skillRelativePath.startsWith(PLATFORM_PACKS_PREFIX) ->
    local.platformPacksRoot.resolve(skillRelativePath.removePrefix(PLATFORM_PACKS_PREFIX))
  skillRelativePath.startsWith(AGENT_ADDONS_PREFIX) ->
    local.repoRoot.resolve(AGENT_ADDONS_PREFIX).resolve(skillRelativePath.removePrefix(AGENT_ADDONS_PREFIX))
  else -> throw ReconciliationConflictError(
    skillRelativePath = skillRelativePath,
    reason = "unrecognized skill-relative category prefix.",
  )
}

private fun reconcileSkillDirectory(upstreamDir: Path, liveDir: Path) {
  val parent = liveDir.toAbsolutePath().normalize().parent
    ?: throw ReconciliationConflictError(
      skillRelativePath = liveDir.toString(),
      reason = "live skill dir has no parent directory.",
    )
  Files.createDirectories(parent)
  val staged = Files.createTempDirectory(parent, ".reconcile-stage-")
  val stagedSkill = staged.resolve(liveDir.fileName.toString())
  copyTreeDeep(upstreamDir, stagedSkill)
  val hasLive = Files.exists(liveDir, LinkOption.NOFOLLOW_LINKS)
  val backup = if (hasLive) Files.createTempDirectory(parent, ".reconcile-backup-") else null

  backup?.let(Files::deleteIfExists)
  try {
    if (hasLive && backup != null) {
      moveDir(liveDir, backup)
    }
    try {
      moveDir(stagedSkill, liveDir)
    } catch (error: IOException) {
      if (backup != null && Files.exists(backup, LinkOption.NOFOLLOW_LINKS) &&
        !Files.exists(liveDir, LinkOption.NOFOLLOW_LINKS)
      ) {
        runCatching { moveDir(backup, liveDir) }
      }
      throw error
    }
  } finally {
    deleteTreeRecursively(staged)
    backup?.let(::deleteTreeRecursively)
  }
}

private fun copyTreeDeep(source: Path, target: Path) {
  Files.walk(source).use { stream ->
    stream.forEach { path ->
      val rel = source.relativize(path)
      val dest = target.resolve(rel.toString())
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        Files.createDirectories(dest)
      } else {
        dest.parent?.let(Files::createDirectories)
        Files.copy(
          path,
          dest,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES,
          LinkOption.NOFOLLOW_LINKS,
        )
      }
    }
  }
}

private fun moveDir(source: Path, target: Path) {
  atomicMoveReplacing(source, target)
}

private fun deleteTreeRecursively(root: Path) {
  if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
    return
  }
  Files.walk(root).use { stream ->
    stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
  }
}
