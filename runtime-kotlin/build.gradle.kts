plugins {
  id("skillbill.version")
  base
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.detekt) apply false
}

group = "dev.skillbill"

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
