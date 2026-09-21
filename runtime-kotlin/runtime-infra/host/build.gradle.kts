plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
  id("skillbill.governed-resources")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":runtime-ports"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-contracts"))
  implementation(project(":runtime-infra:contracts"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
  testFixturesImplementation(project(":runtime-ports"))
  testFixturesImplementation(testFixtures(project(":runtime-ports")))
  testFixturesImplementation(libs.kotlin.test)
}

governedResources {
  missingSourceMessageTemplate.set("\$owner is missing at \$sourcePath.")
  entry(
    GovernedResourceEntry(
      taskName = "copyCodeGraphDependencyDeclaration",
      repoRelativeSource = "orchestration/dependencies/codegraph-dependency.yaml",
      destinationDir = "skillbill/infrastructure/host/codegraph",
      owner = "SKILL-365: pinned CodeGraph dependency declaration",
    ),
  )
  entry(
    GovernedResourceEntry(
      taskName = "copyJavaGuard",
      repoRelativeSource = "build-logic/convention/src/main/resources/skill-bill-java-guard.sh",
      destinationDir = "skillbill/infrastructure/host/jvm",
      owner =
        "SKILL-244: canonical Java guard script. The runtime image must ship the single " +
          "authored guard so gate JVM resolution has a rule",
      sourceFromRuntimeKotlinProject = true,
    ),
  )
}
