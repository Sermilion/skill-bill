import java.io.File

plugins {
  id("skillbill.jvm-library")
  id("skillbill.repo-test")
  id("skillbill.quality")
}

dependencies {
  api(libs.kotlinx.serialization.json)
  implementation(libs.snakeyaml)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.kotlin.test)
}

val canonicalGoalPlanningDiscoveryExclusionsPath: String =
  rootProject.projectDir.parentFile
    .resolve("orchestration/contracts/goal-planning-discovery-exclusions.yaml")
    .absolutePath

val canonicalGoalVerificationBoundaryCapsPath: String =
  rootProject.projectDir.parentFile
    .resolve("orchestration/contracts/goal-verification-boundary-caps.yaml")
    .absolutePath

val canonicalIssueKeySchemaPath: String =
  rootProject.projectDir.parentFile
    .resolve("orchestration/contracts/issue-key-schema.yaml")
    .absolutePath

val copyGoalPlanningDiscoveryExclusions =
  tasks.register<Copy>("copyGoalPlanningDiscoveryExclusions") {
    val contractPath = canonicalGoalPlanningDiscoveryExclusionsPath
    from(contractPath)
    into(
      layout.buildDirectory.dir(
        "generated/skillbill-contracts/skillbill/infrastructure/contracts",
      ),
    )
    inputs.file(contractPath)
    doFirst {
      require(File(contractPath).exists()) {
        "SKILL-174: goal-planning discovery exclusion contract is missing at $contractPath."
      }
    }
  }

val copyGoalVerificationBoundaryCaps =
  tasks.register<Copy>("copyGoalVerificationBoundaryCaps") {
    val contractPath = canonicalGoalVerificationBoundaryCapsPath
    from(contractPath)
    into(
      layout.buildDirectory.dir(
        "generated/skillbill-contracts/skillbill/infrastructure/contracts",
      ),
    )
    inputs.file(contractPath)
    doFirst {
      require(File(contractPath).exists()) {
        "SKILL-202: goal verification boundary caps contract is missing at $contractPath."
      }
    }
  }

val copyIssueKeySchema =
  tasks.register<Copy>("copyIssueKeySchema") {
    val contractPath = canonicalIssueKeySchemaPath
    from(contractPath)
    into(
      layout.buildDirectory.dir(
        "generated/skillbill-contracts/skillbill/infrastructure/contracts",
      ),
    )
    inputs.file(contractPath)
    doFirst {
      require(File(contractPath).exists()) {
        "issue-key schema is missing at $contractPath."
      }
    }
  }

sourceSets.named("main") {
  resources.srcDir(layout.buildDirectory.dir("generated/skillbill-contracts"))
}

listOf("processResources", "processTestResources", "sourcesJar").forEach { consumer ->
  tasks.matching { task -> task.name == consumer }.configureEach {
    dependsOn(
      copyGoalPlanningDiscoveryExclusions,
      copyGoalVerificationBoundaryCaps,
      copyIssueKeySchema,
    )
  }
}
