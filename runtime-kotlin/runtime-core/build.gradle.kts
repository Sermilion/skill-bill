plugins {
  alias(libs.plugins.ksp)
  id("skillbill.jvm-library")
  id("skillbill.quality")
  // SKILL-48 C8: publish the shared `repoRootFromTest()` helper to downstream test code
  // (runtime-core's own tests and runtime-desktop:feature:skillbill jvmTest) via the
  // `java-test-fixtures` plugin so the four prior copies collapse into one source.
  `java-test-fixtures`
}

dependencies {
  api(project(":runtime-application"))
  api(project(":runtime-contracts"))
  api(project(":runtime-domain"))
  api(project(":runtime-infra-fs"))
  api(project(":runtime-infra-http"))
  api(project(":runtime-infra-sqlite"))
  api(project(":runtime-ports"))
  implementation(libs.kotlin.inject.runtime)
  ksp(libs.kotlin.inject.compiler)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}
