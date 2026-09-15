plugins {
  base
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.detekt) apply false
}

group = "dev.skillbill"







version = providers.environmentVariable("RELEASE_VERSION").orNull
  ?.takeIf(String::isNotBlank)
  ?: gitDevSnapshotVersion()
  ?: "0.0.0-SNAPSHOT"

fun gitDevSnapshotVersion(): String? {
  val describe = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
    isIgnoreExitValue = true
  }
  val tag = try {
    if (describe.result.get().exitValue != 0) return null
    describe.standardOutput.asText.get().trim()
  } catch (_: Exception) {
    return null
  }
  val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").matchEntire(tag) ?: return null
  val (major, minor, patch) = match.destructured
  return "$major.$minor.${patch.toInt() + 1}-SNAPSHOT"
}

subprojects {
  group = rootProject.group
  version = rootProject.version
}

val buildLogic = gradle.includedBuild("build-logic")
val buildLogicCheck = buildLogic.task(":convention:check")
val buildLogicDetekt = buildLogic.task(":convention:detekt")
val buildLogicSpotlessCheck = buildLogic.task(":convention:spotlessCheck")

tasks.named("check") {
  dependsOn(buildLogicCheck)
  dependsOn(subprojects.map { subproject -> subproject.tasks.named("check") })
}

tasks.register("detekt") {
  dependsOn(buildLogicDetekt)
  dependsOn(subprojects.mapNotNull { subproject -> subproject.tasks.findByName("detekt") })
}

tasks.register("spotlessCheck") {
  dependsOn(buildLogicSpotlessCheck)
  dependsOn(subprojects.mapNotNull { subproject -> subproject.tasks.findByName("spotlessCheck") })
}
