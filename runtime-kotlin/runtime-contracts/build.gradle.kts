plugins {
  id("skillbill.jvm-library")
  id("skillbill.repo-test")
  id("skillbill.quality")
}

dependencies {
  api(libs.kotlinx.serialization.json)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}
