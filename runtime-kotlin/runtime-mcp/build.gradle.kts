import java.io.File

plugins {
  alias(libs.plugins.ksp)
  application
  id("skillbill.jvm-library")
  id("skillbill.quality")

  id("skillbill.runtime-image")
}

dependencies {

  implementation(project(":runtime-application"))
  implementation(project(":runtime-contracts"))
  implementation(project(":runtime-core"))
  implementation(project(":runtime-domain"))
  implementation(project(":runtime-engine"))
  implementation(project(":runtime-ports"))
  implementation(libs.kotlin.inject.runtime)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.json.schema.validator)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.yaml)
  ksp(libs.kotlin.inject.compiler)

  testImplementation(project(":runtime-infra:host"))
  testImplementation(project(":runtime-infra:workflow"))
  testImplementation(project(":runtime-infra:contracts"))
  testImplementation(project(":runtime-infra:http"))
  testImplementation(project(":runtime-cli"))
  testImplementation(project(":runtime-infra:sqlite"))

  testImplementation(testFixtures(project(":runtime-core")))
  testImplementation(testFixtures(project(":runtime-ports")))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}

application {
  mainClass.set("skillbill.mcp.core.MainKt")
}

tasks.named<JavaExec>("run") {
  standardInput = System.`in`
}

val canonicalTelemetryEventSchemaPath: String =
  rootProject.projectDir.parentFile
    .resolve("orchestration/contracts/telemetry-event-schema.yaml")
    .absolutePath

val copyTelemetryEventSchema =
  tasks.register<Copy>("copyTelemetryEventSchema") {
    val schemaPath = canonicalTelemetryEventSchemaPath
    from(schemaPath)
    into(
      layout.buildDirectory.dir(
        "generated/skillbill-contracts/skillbill/infrastructure/contracts",
      ),
    )
    inputs.file(schemaPath)
    doFirst {
      require(File(schemaPath).exists()) {
        "SKILL-48: canonical telemetry-event schema is missing at $schemaPath. " +
          "Run from the repo root and ensure the schema file exists."
      }
    }
  }

sourceSets.named("main") {
  resources.srcDir(layout.buildDirectory.dir("generated/skillbill-contracts"))
}

tasks.named("processResources") {
  dependsOn(copyTelemetryEventSchema)
}

tasks.named("processTestResources") {
  dependsOn(copyTelemetryEventSchema)
}

runtimeImage {
  imageBaseName.set("runtime-mcp")
}
