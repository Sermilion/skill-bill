plugins {
  id("skillbill.kotlin-jvm")
  id("skillbill.kotlin-quality")
}

group = "dev.skillbill"
version = "0.1.0-SNAPSHOT"

dependencies {
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}
