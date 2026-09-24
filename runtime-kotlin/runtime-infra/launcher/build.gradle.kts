plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
}

dependencies {
  implementation(project(":runtime-ports"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-contracts"))
  implementation(project(":runtime-infra:skills"))
  implementation(project(":runtime-infra:host"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.jackson.databind)
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(testFixtures(project(":runtime-infra:host")))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}
