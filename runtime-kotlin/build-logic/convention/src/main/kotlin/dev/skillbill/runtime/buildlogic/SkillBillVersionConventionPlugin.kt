package dev.skillbill.runtime.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class SkillBillVersionConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val releaseVersion = target.providers.environmentVariable("RELEASE_VERSION").orNull
    target.version = resolveSkillBillVersion(releaseVersion, gitListedVersionTags(target))
  }
}

private fun gitListedVersionTags(project: Project): List<String> {
  val listed =
    project.providers.exec {
      commandLine("git", "tag", "-l", "v[0-9]*")
      isIgnoreExitValue = true
    }
  return try {
    val exitValue = listed.result.get().exitValue
    if (exitValue != 0) {
      project.logger.warn(versionTagFallbackMessage("git tag -l exited with $exitValue"))
      emptyList()
    } else {
      listed.standardOutput.asText.get()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    }
  } catch (expectedGitFailure: Exception) {
    project.logger.warn(versionTagFallbackMessage(expectedGitFailure.toString()), expectedGitFailure)
    emptyList()
  }
}

private fun versionTagFallbackMessage(cause: String): String =
  "Cannot list git version tags ($cause). Falling back to the unversioned $UNVERSIONED_SNAPSHOT build version; " +
    "release artifacts built from this checkout will not carry a release version."
