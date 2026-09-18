plugins {
  alias(libs.plugins.ksp)
  id("skillbill.jvm-library")
  id("skillbill.quality")

  `java-test-fixtures`
}

tasks.named<ProcessResources>("processResources") {
  val skillBillVersion = project.version.toString()
  inputs.property("skillBillVersion", skillBillVersion)
  filesMatching("skillbill/version.properties") {
    expand("skillBillVersion" to skillBillVersion)
  }
}

dependencies {
  api(project(":runtime-application"))
  api(project(":runtime-ports"))
  api(project(":runtime-engine"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-contracts"))
  testImplementation(libs.kotlinx.serialization.json)
  implementation(project(":runtime-infra:fs"))
  implementation(project(":runtime-infra:http"))
  implementation(project(":runtime-infra:sqlite"))
  implementation(libs.kotlin.inject.runtime)
  ksp(libs.kotlin.inject.compiler)

  testImplementation(testFixtures(project(":runtime-application")))
  testImplementation(testFixtures(project(":runtime-engine")))
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(testFixtures(project(":runtime-domain")))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.jackson.dataformat.yaml)
}
