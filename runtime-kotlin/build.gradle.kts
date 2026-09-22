plugins {
  id("skillbill.version")
  base
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
