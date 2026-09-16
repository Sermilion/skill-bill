import dev.skillbill.runtime.buildlogic.resolveSkillBillVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

class SkillBillVersionConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val releaseVersion = target.providers.environmentVariable("RELEASE_VERSION").orNull
    target.version = resolveSkillBillVersion(releaseVersion, gitListedVersionTags(target))
  }
}

private fun gitListedVersionTags(project: Project): List<String> {
  val listed = project.providers.exec {
    commandLine("git", "tag", "-l", "v[0-9]*")
    isIgnoreExitValue = true
  }
  return try {
    if (listed.result.get().exitValue != 0) {
      emptyList()
    } else {
      listed.standardOutput.asText.get()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toList()
    }
  } catch (_: Exception) {
    emptyList()
  }
}
