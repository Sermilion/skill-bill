plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":runtime-ports"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-contracts"))
  implementation(project(":runtime-infra:contracts"))
  implementation(project(":runtime-infra:host"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.snakeyaml)
  implementation(libs.json.schema.validator)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(testFixtures(project(":runtime-infra:host")))
  testImplementation(project(":runtime-infra:workflow"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
  testFixturesImplementation(project(":runtime-ports"))
  testFixturesImplementation(project(":runtime-domain"))
  testFixturesImplementation(libs.kotlin.test)
}
