import dev.skillbill.runtime.buildlogic.GovernedResourceEntry

plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
  id("skillbill.governed-resources")
}

dependencies {
  implementation(project(":runtime-ports"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-contracts"))
  implementation(project(":runtime-infra:skills"))
  implementation(project(":runtime-infra:contracts"))
  implementation(project(":runtime-infra:host"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.json.schema.validator)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(testFixtures(project(":runtime-infra:host")))
  testImplementation(testFixtures(project(":runtime-infra:skills")))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}

governedResources {
  missingSourceMessageTemplate.set("\$owner is missing at \$sourcePath.")
  entry(
    GovernedResourceEntry(
      taskName = "copySpecialistContract",
      repoRelativeSource = "orchestration/review-orchestrator/specialist-contract.md",
      destinationDir = "skillbill/review",
      owner = "Authoritative delegated-review specialist contract",
      requireSourceIsFile = true,
    ),
  )
}
