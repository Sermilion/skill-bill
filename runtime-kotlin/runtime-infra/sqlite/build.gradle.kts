plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
}

tasks.named<ProcessResources>("processResources") {
  val skillBillVersion = project.version.toString()
  inputs.property("skillBillVersion", skillBillVersion)
  filesMatching("skillbill/version.properties") {
    expand("skillBillVersion" to skillBillVersion)
  }
}

dependencies {
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-ports"))
  implementation(project(":runtime-contracts"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.sqlite.jdbc)
  implementation(libs.json.schema.validator)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}
