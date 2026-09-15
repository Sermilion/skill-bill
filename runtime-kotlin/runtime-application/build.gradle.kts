plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")

  `java-test-fixtures`
}

dependencies {
  api(project(":runtime-contracts"))
  api(project(":runtime-domain"))
  api(project(":runtime-ports"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.kotlinx.serialization.json)

  testFixturesImplementation(project(":runtime-infra-fs"))
  testFixturesImplementation(project(":runtime-infra-sqlite"))
  testFixturesImplementation(testFixtures(project(":runtime-ports")))
  testFixturesImplementation(testFixtures(project(":runtime-domain")))
  testFixturesImplementation(libs.kotlin.test)
  testFixturesImplementation(libs.jackson.dataformat.yaml)
  testImplementation(testFixtures(project(":runtime-application")))
  testImplementation(testFixtures(project(":runtime-engine")))
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(testFixtures(project(":runtime-domain")))
  testImplementation(project(":runtime-infra-fs"))
  testImplementation(project(":runtime-infra-sqlite"))
  testImplementation(project(":runtime-engine"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.jackson.dataformat.yaml)
}
