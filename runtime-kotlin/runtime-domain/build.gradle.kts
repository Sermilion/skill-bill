plugins {
  id("skillbill.jvm-library")
  id("skillbill.quality")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":runtime-contracts"))
  testImplementation(libs.jackson.databind)
  testImplementation(libs.jackson.dataformat.yaml)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}
